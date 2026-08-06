package dbadmin.backend.service;

import dbadmin.backend.aop.BusinessLog;
import dbadmin.backend.ddl.ColumnType;
import dbadmin.backend.ddl.TableDdlExecutor;
import dbadmin.backend.entity.HedefTip;
import dbadmin.backend.entity.IslemTipi;
import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.KolonRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TabloRepository;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.tracing.SpanRunner;
import dbadmin.backend.validation.NameValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Is mantiginin (business logic) yasadigi yer — controller'lar burayi cagirir, burasi degil onlari.
 * Her public metod iki isi birden yapar: (1) metadata'yi ({@code Tablo}/{@code Kolon} satirlari)
 * gunceller, (2) {@link TableDdlExecutor} uzerinden gercek Postgres semasini ayni yonde degistirir.
 * Ikisi de ayni {@code @Transactional} icinde oldugu icin biri patlarsa oburu de geri alinir,
 * boylece metadata ile gercek DB semasi hicbir zaman birbirinden kopmaz.
 */
@Service
public class TabloService {

    private final TabloRepository tabloRepository;
    private final KolonRepository kolonRepository;
    private final TagRepository tagRepository;
    private final SchemaRepository schemaRepository;
    private final TableDdlExecutor ddlExecutor;
    private final SpanRunner spanRunner;
    private final AuditLogService auditLogService;

    // Is olaylarini sayan kumulatif sayaclar (Prometheus'ta _total soneki alir) — "kac tablo
    // olusturuldu" gibi sorular icin. Anlik durum ("su an kac tablo var") icin ise Gauge
    // kullaniliyor (asagida, constructor'da kaydediliyor) cunku o hep DB'nin canli sayisini yansitir.
    private final Counter tablesCreatedCounter;
    private final Counter tablesDeletedCounter;
    private final Counter columnsCreatedCounter;

    public TabloService(TabloRepository tabloRepository, KolonRepository kolonRepository,
            TagRepository tagRepository, SchemaRepository schemaRepository, TableDdlExecutor ddlExecutor,
            SpanRunner spanRunner, AuditLogService auditLogService, MeterRegistry meterRegistry) {
        this.tabloRepository = tabloRepository;
        this.kolonRepository = kolonRepository;
        this.tagRepository = tagRepository;
        this.schemaRepository = schemaRepository;
        this.ddlExecutor = ddlExecutor;
        this.spanRunner = spanRunner;
        this.auditLogService = auditLogService;
        // Dikkat: isim ".created" ile bitmesin — Prometheus/OpenMetrics'te "_created" ayri bir
        // anlam tasiyan (sayacin olusturulma zaman damgasi) rezerve bir sonek; Micrometer bunu
        // fark edip "created" kelimesini sessizce siliyor (ör. "dbadmin.tables.created" ->
        // "dbadmin_tables_total" olarak cikiyor, "created" kayboluyor). "creations" cakismiyor.
        this.tablesCreatedCounter = meterRegistry.counter("dbadmin.tables.creations");
        this.tablesDeletedCounter = meterRegistry.counter("dbadmin.tables.deletions");
        this.columnsCreatedCounter = meterRegistry.counter("dbadmin.columns.creations");
        meterRegistry.gauge("dbadmin.tables.active", tabloRepository, TabloRepository::count);
    }
    /**
     * {@code @Transactional}: bu metod icindeki tum DB islemlerini tek bir islem paketine alir;
     * hepsi basarili olursa commit eder, biri hata verirse tumunu geri alir (rollback).
     * {@code readOnly = true} sadece okuma yapildigi icin performans ipucu — yazma islemi yapmaz.
     */
    @Transactional(readOnly = true)
    public List<Tablo> listTablolar() {
        return tabloRepository.findAllByOrderByNameAsc().stream()
                .filter(tablo -> !SchemaService.isHidden(tablo.getSchema()))
                .toList();
    }

    /** Sidebar'daki schema -> tablo hiyerarsisi icin: sadece bir schema'nin altindaki tablolar, isme gore alfabetik sirali. */
    @Transactional(readOnly = true)
    public List<Tablo> listTablolarBySchema(Long schemaId) {
        return tabloRepository.findBySchemaIdOrderByNameAsc(schemaId);
    }

    /**
     * Id ile tek tablo bulur; yoksa 404'e cevrilecek {@link NotFoundException} firlatir (bkz.
     * GlobalExceptionHandler). Gizli schema'daki ("public") eski satirlar da bulunamamis sayilir
     * (bkz. {@link SchemaService#RESERVED_SCHEMA_NAME}) — bu metodu tum yazma islemleri de
     * kullandigi icin, oradaki bir tablo web uzerinden okunamaz da degistirilemez de.
     */
    @Transactional(readOnly = true)
    public Tablo getTablo(Long id) {
        return tabloRepository.findById(id)
                .filter(tablo -> !SchemaService.isHidden(tablo.getSchema()))
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TABLE", "tablo not found: " + id, Map.of("id", String.valueOf(id))));
    }

    /**
     * Yeni tablo olusturur: once metadata (Tablo + Kolon satirlari) DB'ye yazilir, sonra ayni
     * transaction icinde gercek {@code CREATE TABLE} calistirilir. DDL patlarsa metadata insert'i
     * de otomatik geri alinir — iki katman asla birbirinden kopmaz.
     * {@code schemaId} zorunlu: tablonun hangi schema'ya kurulacagi acikca belirtilmeli. Eskiden
     * null gelince tablo "public"e kuruluyordu; "public" artik gizli oldugu icin (bkz.
     * {@link SchemaService#RESERVED_SCHEMA_NAME}) boyle bir tablo olusturuldugu anda arayuzde
     * gorunmez olurdu — o yuzden sessiz varsayilan yerine hata veriyoruz.
     */
    @BusinessLog("tablo-olusturuldu")
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public Tablo createTablo(String name, Long schemaId, List<KolonTanimi> kolonTanimlari) {
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", name);
        if (tabloRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + name + "' already exists",
                    Map.of("name", name));
        }
        if (kolonTanimlari == null || kolonTanimlari.isEmpty()) {
            throw new ValidationException("VALIDATION_TABLE_NEEDS_COLUMN", "a table needs at least one column");
        }
        Schema schema = resolveSchema(schemaId);

        Tablo tablo = new Tablo(name);
        tablo.setSchema(schema);
        Set<String> seenNames = new HashSet<>();
        List<TableDdlExecutor.ColumnDefinition> ddlColumns = new ArrayList<>();
        List<String> primaryKeyColumnNames = new ArrayList<>();

        for (KolonTanimi tanim : kolonTanimlari) {
            NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", tanim.name());
            if (!seenNames.add(tanim.name())) {
                throw new ConflictException(
                        "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
                        "duplicate column name in request: " + tanim.name(),
                        Map.of("name", tanim.name()));
            }
            ColumnType type = ColumnType.fromMetadataValue(tanim.type());

            Kolon kolon = new Kolon(tanim.name(), type.metadataValue(), tablo);
            kolon.setTag(resolveTag(tanim.tagId()));
            kolon.setPrimaryKey(tanim.primaryKey());
            tablo.addKolon(kolon);
            ddlColumns.add(new TableDdlExecutor.ColumnDefinition(tanim.name(), type));
            if (tanim.primaryKey()) {
                primaryKeyColumnNames.add(tanim.name());
            }
        }

        Tablo saved = spanRunner.inSpan("metadata-write", () -> tabloRepository.save(tablo));
        // PK isaretli kolonlar CREATE TABLE'in kendi icinde veriliyor (ayri bir ALTER TABLE ile
        // degil): boylece gercek tablo tek ifadede, elle yazilmis bir
        // "CREATE TABLE ... PRIMARY KEY (col1, col2)" ile birebir ayni sekilde olusuyor.
        spanRunner.inSpan("ddl-execute", "db.table.name", saved.getName(),
                () -> ddlExecutor.createTable(schema.getName(), saved.getName(), ddlColumns, primaryKeyColumnNames));
        tablesCreatedCounter.increment();
        columnsCreatedCounter.increment(ddlColumns.size());
        auditLogService.kaydet(IslemTipi.TABLO_OLUSTURULDU, HedefTip.TABLO, saved.getId(),
                "tablo olusturuldu: " + saved.getName() + " (schema=" + schema.getName() + ")");
        return saved;
    }

    /**
     * Verilen id'deki Schema'yi doner. {@code null} id 400, bulunamayan id 404 verir; gizli
     * ("public") schema da bulunamamis sayilir, yani tablo ne oraya kurulabilir ne de oraya
     * tasinabilir.
     */
    private Schema resolveSchema(Long schemaId) {
        if (schemaId == null) {
            throw new ValidationException("VALIDATION_MISSING_SCHEMA", "a schema must be specified");
        }
        return schemaRepository.findById(schemaId)
                .filter(schema -> !SchemaService.isHidden(schema))
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_SCHEMA", "schema not found: " + schemaId, Map.of("id", String.valueOf(schemaId))));
    }

    /**
     * Var olan bir tabloyu baska bir schema'ya tasir: hem metadata (Tablo.schema) hem gercek
     * Postgres tablosu ({@code ALTER TABLE ... SET SCHEMA}) birlikte gider. Hedef schema zorunlu
     * ve gercek bir schema olmali: "public" gizli oldugu icin (bkz.
     * {@link SchemaService#RESERVED_SCHEMA_NAME}) oraya tasima 404 ile reddedilir.
     */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public Tablo changeSchema(Long tabloId, Long newSchemaId) {
        String eskiSchemaAdi = getTablo(tabloId).getSchema().getName();
        Tablo tablo = changeSchemaCore(tabloId, newSchemaId);
        if (!eskiSchemaAdi.equals(tablo.getSchema().getName())) {
            auditLogService.kaydet(IslemTipi.TABLO_SEMASI_DEGISTIRILDI, HedefTip.TABLO, tabloId,
                    "schema degisti: " + eskiSchemaAdi + " -> " + tablo.getSchema().getName());
        }
        return tablo;
    }

    private Tablo changeSchemaCore(Long tabloId, Long newSchemaId) {
        Tablo tablo = getTablo(tabloId);
        Schema newSchema = resolveSchema(newSchemaId);
        Schema currentSchema = tablo.getSchema();
        if (currentSchema.getId().equals(newSchema.getId())) {
            return tablo;
        }
        ddlExecutor.moveTableToSchema(currentSchema.getName(), tablo.getName(), newSchema.getName());
        tablo.setSchema(newSchema);
        tablo.touch();
        return tablo;
    }

    /** Hem metadata'daki (Tablo.name) hem gercek Postgres tablosunun adini degistirir. */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public Tablo renameTablo(Long id, String newName) {
        String eskiAd = getTablo(id).getName();
        Tablo tablo = renameTabloCore(id, newName);
        if (!eskiAd.equals(tablo.getName())) {
            auditLogService.kaydet(IslemTipi.TABLO_YENIDEN_ADLANDIRILDI, HedefTip.TABLO, id,
                    "isim degisti: " + eskiAd + " -> " + tablo.getName());
        }
        return tablo;
    }

    private Tablo renameTabloCore(Long id, String newName) {
        Tablo tablo = getTablo(id);
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", newName);
        if (tablo.getName().equals(newName)) {
            // Postgres "ALTER TABLE x RENAME TO x" ifadesini "relation already exists" diye
            // reddediyor — yeni isim eskiyle ayniysa DDL'e hic gitmiyoruz, zaten yapilacak bir
            // sey yok.
            return tablo;
        }
        if (tabloRepository.existsByName(newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + newName + "' already exists",
                    Map.of("name", newName));
        }
        String oldName = tablo.getName();
        tablo.setName(newName);
        tablo.touch();
        ddlExecutor.renameTable(tablo.getSchema().getName(), oldName, newName);
        ddlExecutor.renamePrimaryKeyConstraintIfExists(tablo.getSchema().getName(), oldName, newName);
        return tablo;
    }

    /** Tablo silinince {@code tabloRepository.delete} JPA cascade sayesinde altindaki tum Kolon satirlarini da siler; sonra gercek tablo da drop edilir. */
    @BusinessLog("tablo-silindi")
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public void deleteTablo(Long id) {
        Tablo tablo = getTablo(id);
        String name = tablo.getName();
        String schemaName = tablo.getSchema().getName();
        spanRunner.inSpan("metadata-write", () -> tabloRepository.delete(tablo));
        spanRunner.inSpan("ddl-execute", "db.table.name", name, () -> ddlExecutor.dropTable(schemaName, name));
        tablesDeletedCounter.increment();
        auditLogService.kaydet(IslemTipi.TABLO_SILINDI, HedefTip.TABLO, id, "tablo silindi: " + name);
    }

    /** Mevcut bir tabloya yeni kolon ekler; metadata + gercek {@code ALTER TABLE ADD COLUMN} birlikte gider. */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public Kolon addKolon(Long tabloId, KolonTanimi tanim) {
        Kolon kolon = addKolonCore(tabloId, tanim);
        auditLogService.kaydet(IslemTipi.KOLON_EKLENDI, HedefTip.KOLON, kolon.getId(),
                "kolon eklendi: " + kolon.getName() + " (tablo=" + tabloId + ")");
        return kolon;
    }

    private Kolon addKolonCore(Long tabloId, KolonTanimi tanim) {
        Tablo tablo = getTablo(tabloId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", tanim.name());
        if (kolonRepository.existsByTabloAndName(tablo, tanim.name())) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + tanim.name() + "' already exists in this table",
                    Map.of("name", tanim.name()));
        }
        ColumnType type = ColumnType.fromMetadataValue(tanim.type());
        Kolon kolon = new Kolon(tanim.name(), type.metadataValue(), tablo);
        kolon.setTag(resolveTag(tanim.tagId()));
        kolon.setPrimaryKey(tanim.primaryKey());
        tablo.addKolon(kolon);
        tablo.touch();
        Kolon saved = kolonRepository.save(kolon);

        ddlExecutor.addColumn(tablo.getSchema().getName(), tablo.getName(), saved.getName(), type);
        columnsCreatedCounter.increment();
        if (tanim.primaryKey()) {
            // Yeni kolon da isarete katildigi icin tum PK-isaretli kolon seti degisti: bir
            // tablonun tek bir primary key'i olabildigi icin eskisini kaldirip genisletilmis
            // setle (composite olarak) yeniden kuruyoruz.
            syncPrimaryKeyConstraint(tablo);
        }
        return saved;
    }

    /**
     * Dogrudan {@code kolonRepository.delete(...)} cagirmiyoruz — bilerek {@code tablo.removeKolon(kolon)}
     * kullaniyoruz, cunku Kolon'u Tablo'nun kendi listesinden cikarmak, Tablo entity'sindeki
     * {@code orphanRemoval = true} sayesinde otomatik DB'den silinmesini tetikler.
     */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public void deleteKolon(Long tabloId, Long kolonId) {
        String columnName = findKolonInTablo(getTablo(tabloId), kolonId).getName();
        deleteKolonCore(tabloId, kolonId);
        auditLogService.kaydet(IslemTipi.KOLON_SILINDI, HedefTip.KOLON, kolonId,
                "kolon silindi: " + columnName + " (tablo=" + tabloId + ")");
    }

    private void deleteKolonCore(Long tabloId, Long kolonId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        String columnName = kolon.getName();
        boolean wasPrimaryKey = kolon.isPrimaryKey();
        if (wasPrimaryKey) {
            // Composite primary key dusen kolona da bagli oldugu icin, kolonu gercekten silmeden
            // once constraint'i tamamen kaldiriyoruz (kalan PK-isaretli kolonlar varsa asagida
            // yeniden kuruluyor).
            ddlExecutor.dropPrimaryKeyConstraintIfExists(tablo.getSchema().getName(), tablo.getName());
        }
        tablo.removeKolon(kolon);
        tablo.touch();
        ddlExecutor.dropColumn(tablo.getSchema().getName(), tablo.getName(), columnName);
        if (wasPrimaryKey) {
            List<String> remainingPrimaryKeyColumnNames = tablo.getKolonlar().stream()
                    .filter(Kolon::isPrimaryKey)
                    .map(Kolon::getName)
                    .toList();
            if (!remainingPrimaryKeyColumnNames.isEmpty()) {
                ddlExecutor.addPrimaryKeyConstraint(
                        tablo.getSchema().getName(), tablo.getName(), remainingPrimaryKeyColumnNames);
            }
        }
    }

    /**
     * Tablonun su anki PK-isaretli kolonlarina gore gercek {@code PRIMARY KEY} constraint'ini
     * yeniden kurar: her zaman once kaldirir, sonra (bos degilse) guncel kolon setiyle tekrar
     * ekler. Bir tablonun birden fazla primary key'i olamadigi icin "genislet" diye bir islem
     * yok — dogru yol hep drop + yeniden add. Cagiran taraf (addKolon/deleteKolon) sadece PK
     * setinin degistigi anlarda cagirir.
     */
    private void syncPrimaryKeyConstraint(Tablo tablo) {
        List<String> primaryKeyColumnNames = tablo.getKolonlar().stream()
                .filter(Kolon::isPrimaryKey)
                .map(Kolon::getName)
                .toList();
        ddlExecutor.dropPrimaryKeyConstraintIfExists(tablo.getSchema().getName(), tablo.getName());
        if (!primaryKeyColumnNames.isEmpty()) {
            ddlExecutor.addPrimaryKeyConstraint(tablo.getSchema().getName(), tablo.getName(), primaryKeyColumnNames);
        }
    }

    /** Kolonun sadece adini degistirir — tipi olusturulduktan sonra hic degistirilemez (bkz. Kolon.type, updatable=false). */
    @Transactional
    public Kolon renameKolon(Long tabloId, Long kolonId, String newName) {
        String eskiAd = findKolonInTablo(getTablo(tabloId), kolonId).getName();
        Kolon kolon = renameKolonCore(tabloId, kolonId, newName);
        if (!eskiAd.equals(kolon.getName())) {
            auditLogService.kaydet(IslemTipi.KOLON_YENIDEN_ADLANDIRILDI, HedefTip.KOLON, kolonId,
                    "kolon adi degisti: " + eskiAd + " -> " + kolon.getName());
        }
        return kolon;
    }

    private Kolon renameKolonCore(Long tabloId, Long kolonId, String newName) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", newName);
        if (kolon.getName().equals(newName)) {
            // Postgres "ALTER TABLE ... RENAME COLUMN x TO x" ifadesini "column already exists"
            // diye reddediyor — yeni isim eskiyle ayniysa DDL'e hic gitmiyoruz.
            return kolon;
        }
        if (kolonRepository.existsByTabloAndName(tablo, newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + newName + "' already exists in this table",
                    Map.of("name", newName));
        }
        String oldName = kolon.getName();
        kolon.setName(newName);
        tablo.touch();
        ddlExecutor.renameColumn(tablo.getSchema().getName(), tablo.getName(), oldName, newName);
        return kolon;
    }

    /**
     * Var olan bir kolonun birincil anahtar isaretini degistirir ve tablonun gercek
     * {@code PRIMARY KEY} constraint'ini yeni isaretli kolon setiyle yeniden kurar.
     * <p>
     * Bu uc, {@code primaryKey}'in gercek bir PRIMARY KEY'e donusmesiyle birlikte gerekli hale
     * geldi: bayrak eskiden sadece kozmetikti ve yalnizca kolon olusturulurken (createTablo /
     * addKolon) set edilebiliyordu, dolayisiyla var olan bir kolonu PK yapmanin tek yolu onu
     * silip yeniden eklemekti — yani icindeki veriyi kaybetmekti.
     * <p>
     * Bilinen sinir: tabloda satir varken NULL iceren bir kolonu PK yapmak Postgres tarafindan
     * reddedilir (PK kolonlari otomatik {@code NOT NULL} olur); ayni sekilde tekrar eden degerler
     * varsa da reddedilir. Her iki durumda da transaction geri alindigi icin metadata'daki isaret
     * de degismez, iki katman ayrisamaz.
     */
    @Transactional
    public Kolon changeKolonPrimaryKey(Long tabloId, Long kolonId, boolean primaryKey) {
        boolean eskiDeger = findKolonInTablo(getTablo(tabloId), kolonId).isPrimaryKey();
        Kolon kolon = changeKolonPrimaryKeyCore(tabloId, kolonId, primaryKey);
        if (eskiDeger != kolon.isPrimaryKey()) {
            auditLogService.kaydet(IslemTipi.KOLON_PRIMARY_KEY_DEGISTIRILDI, HedefTip.KOLON, kolonId,
                    "primary key " + (kolon.isPrimaryKey() ? "eklendi" : "kaldirildi") + ": " + kolon.getName());
        }
        return kolon;
    }

    private Kolon changeKolonPrimaryKeyCore(Long tabloId, Long kolonId, boolean primaryKey) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        if (kolon.isPrimaryKey() == primaryKey) {
            // Isaret zaten istenen durumda: gereksiz yere DROP + ADD CONSTRAINT calistirmiyoruz.
            return kolon;
        }
        kolon.setPrimaryKey(primaryKey);
        tablo.touch();
        syncPrimaryKeyConstraint(tablo);
        return kolon;
    }

    /** Tag'in gercek DB semasinda hic karsiligi yok (sadece metadata) — o yuzden burada ddlExecutor cagrisi yok, sade bir entity guncellemesi. */
    @Transactional
    public Kolon changeKolonTag(Long tabloId, Long kolonId, Long tagId) {
        Kolon kolon = changeKolonTagCore(tabloId, kolonId, tagId);
        auditLogService.kaydet(IslemTipi.KOLON_TAG_DEGISTIRILDI, HedefTip.KOLON, kolonId,
                "tag degisti: kolon=" + kolon.getName() + " tagId=" + tagId);
        return kolon;
    }

    private Kolon changeKolonTagCore(Long tabloId, Long kolonId, Long tagId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        kolon.setTag(resolveTag(tagId));
        tablo.touch();
        return kolon;
    }

    /**
     * "Kaydet'e basinca hepsi birden gitsin" akisinin karsiligi: bir tablo uzerinde biriktirilmis
     * TUM degisiklikleri (isim, schema, silinen/eklenen/guncellenen kolonlar) tek seferde uygular.
     * <p>
     * Dikkat cekici implementasyon detayi: burada DDL'i yeniden yazmiyoruz, sadece yukaridaki
     * (zaten test edilmis) metodlarin audit'siz *Core varyantlarini ({@code renameTabloCore}/
     * {@code changeSchemaCore}/{@code deleteKolonCore}/{@code renameKolonCore}/
     * {@code changeKolonTagCore}/{@code changeKolonPrimaryKeyCore}/{@code addKolonCore}) sirayla
     * {@code this.} uzerinden cagiriyoruz — public (audit'li) versiyonlari degil, cunku aksi
     * halde tek bir "Kaydet" tiklamasi N ayri audit satirina bolunurdu (bkz. AuditLogService); bu
     * metod, yapilanlarin tek bir ozetini en sonda tek satir olarak yazar. *Core metodlar da
     * {@code @Transactional} ama Spring'in proxy-tabanli AOP'si self-invocation'da (ayni sinifin
     * kendi icinden {@code this.metod()} cagirmak) devreye girmez — yani bu cagrilar kendi ayri
     * transaction'larini ACMAZ, hepsi bu metodun disaridan (controller'dan) tetiklenirken acilmis
     * TEK transaction'a katilir. Sonuc: N alt-islemden biri patlarsa (ör. bir kolonu PK yapmak
     * NULL degerler yuzunden reddedilirse), o ana kadar uygulanmis olan hicbir sey (rename,
     * silinen kolonlar, eklenen kolonlar, henuz yazilmamis audit ozeti) de kalici olmaz — hepsi
     * ya da hicbiri.
     * <p>
     * Sira: rename -> schema tasi -> sil -> guncelle -> ekle. Sirayla PK degisen her kolon kendi
     * {@code syncPrimaryKeyConstraint} cagrisini tetikler (N kolon degisirse constraint N kez
     * yeniden kurulur) — dogru sonucu verir ama en verimli yol degildir; tek bir Kaydet'te
     * beklenen kolon sayisi kucuk oldugu icin simdilik bu kabul edilebilir bir maliyet.
     */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public Tablo applyChanges(Long tabloId, String yeniIsim, Long yeniSchemaId,
            List<Long> silinecekKolonIdler, List<KolonTanimi> eklenecekKolonlar,
            List<KolonGuncelleme> guncellenecekKolonlar) {
        // Alt-islemler icin *Core metodlar cagriliyor (public renameTablo/addKolon/... degil) —
        // her biri kendi audit satirini yazsaydi, tek bir "Kaydet" tiklamasi N adet audit satirina
        // bolunurdu. Bunun yerine burada tek bir ozet toplanip en sonda TEK satir yaziliyor.
        List<String> degisiklikler = new ArrayList<>();
        if (yeniIsim != null) {
            String eskiAd = getTablo(tabloId).getName();
            Tablo sonra = renameTabloCore(tabloId, yeniIsim);
            if (!eskiAd.equals(sonra.getName())) {
                degisiklikler.add("isim: " + eskiAd + " -> " + sonra.getName());
            }
        }
        if (yeniSchemaId != null) {
            String eskiSchema = getTablo(tabloId).getSchema().getName();
            Tablo sonra = changeSchemaCore(tabloId, yeniSchemaId);
            if (!eskiSchema.equals(sonra.getSchema().getName())) {
                degisiklikler.add("schema: " + eskiSchema + " -> " + sonra.getSchema().getName());
            }
        }
        for (Long kolonId : silinecekKolonIdler) {
            String columnName = findKolonInTablo(getTablo(tabloId), kolonId).getName();
            deleteKolonCore(tabloId, kolonId);
            degisiklikler.add("kolon silindi: " + columnName);
        }
        for (KolonGuncelleme guncelleme : guncellenecekKolonlar) {
            renameKolonCore(tabloId, guncelleme.kolonId(), guncelleme.yeniIsim());
            changeKolonTagCore(tabloId, guncelleme.kolonId(), guncelleme.yeniTagId());
            changeKolonPrimaryKeyCore(tabloId, guncelleme.kolonId(), guncelleme.yeniPrimaryKey());
            degisiklikler.add("kolon guncellendi: id=" + guncelleme.kolonId());
        }
        for (KolonTanimi yeni : eklenecekKolonlar) {
            Kolon eklenen = addKolonCore(tabloId, yeni);
            degisiklikler.add("kolon eklendi: " + eklenen.getName());
        }
        Tablo sonucTablo = getTablo(tabloId);
        if (!degisiklikler.isEmpty()) {
            auditLogService.kaydet(IslemTipi.TABLO_GUNCELLENDI, HedefTip.TABLO, tabloId,
                    String.join("; ", degisiklikler));
        }
        return sonucTablo;
    }

    /** Kolonu, tablonun zaten yuklu {@code kolonlar} listesi icinde arar (ekstra sorgu atmadan) — kolon bu tabloya ait degilse 404 doner. */
    private Kolon findKolonInTablo(Tablo tablo, Long kolonId) {
        return tablo.getKolonlar().stream()
                .filter(k -> k.getId().equals(kolonId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_COLUMN",
                        "column not found in this table: " + kolonId,
                        Map.of("id", String.valueOf(kolonId))));
    }

    /** tagId null ise (kullanici tag secmediyse) null doner; doluysa o Tag'i bulur, yoksa 404. */
    private Tag resolveTag(Long tagId) {
        if (tagId == null) {
            return null;
        }
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TAG", "tag not found: " + tagId, Map.of("id", String.valueOf(tagId))));
    }
}
