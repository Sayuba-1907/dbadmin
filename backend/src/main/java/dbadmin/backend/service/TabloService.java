package dbadmin.backend.service;

import dbadmin.backend.ddl.ColumnType;
import dbadmin.backend.ddl.TableDdlExecutor;
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
import dbadmin.backend.validation.NameValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Is olaylarini sayan kumulatif sayaclar (Prometheus'ta _total soneki alir) — "kac tablo
    // olusturuldu" gibi sorular icin. Anlik durum ("su an kac tablo var") icin ise Gauge
    // kullaniliyor (asagida, constructor'da kaydediliyor) cunku o hep DB'nin canli sayisini yansitir.
    private final Counter tablesCreatedCounter;
    private final Counter tablesDeletedCounter;
    private final Counter columnsCreatedCounter;

    public TabloService(TabloRepository tabloRepository, KolonRepository kolonRepository,
            TagRepository tagRepository, SchemaRepository schemaRepository, TableDdlExecutor ddlExecutor,
            MeterRegistry meterRegistry) {
        this.tabloRepository = tabloRepository;
        this.kolonRepository = kolonRepository;
        this.tagRepository = tagRepository;
        this.schemaRepository = schemaRepository;
        this.ddlExecutor = ddlExecutor;
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
        return tabloRepository.findAll();
    }

    /** Sidebar'daki schema -> tablo hiyerarsisi icin: sadece bir schema'nin altindaki tablolar. */
    @Transactional(readOnly = true)
    public List<Tablo> listTablolarBySchema(Long schemaId) {
        return tabloRepository.findBySchemaId(schemaId);
    }

    /** Id ile tek tablo bulur; yoksa 404'e cevrilecek {@link NotFoundException} firlatir (bkz. GlobalExceptionHandler). */
    @Transactional(readOnly = true)
    public Tablo getTablo(Long id) {
        return tabloRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TABLE", "tablo not found: " + id, Map.of("id", String.valueOf(id))));
    }

    /**
     * Yeni tablo olusturur: once metadata (Tablo + Kolon satirlari) DB'ye yazilir, sonra ayni
     * transaction icinde gercek {@code CREATE TABLE} calistirilir. DDL patlarsa metadata insert'i
     * de otomatik geri alinir — iki katman asla birbirinden kopmaz.
     * {@code schemaId} null gelirse tablo varsayilan olarak "public" schema'ya baglanir (bkz.
     * {@link #resolveSchema}); dolu gelirse o id'deki Schema'ya baglanir, yoksa 404.
     */
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

        Tablo saved = tabloRepository.save(tablo);
        ddlExecutor.createTable(schema.getName(), saved.getName(), ddlColumns);
        if (!primaryKeyColumnNames.isEmpty()) {
            ddlExecutor.addPrimaryKeyUniqueConstraint(schema.getName(), saved.getName(), primaryKeyColumnNames);
        }
        tablesCreatedCounter.increment();
        columnsCreatedCounter.increment(ddlColumns.size());
        return saved;
    }

    /** {@code schemaId} null ise varsayilan "public" Schema satirini doner (bkz. SchemaBootstrapRunner), doluysa o id'yi arar, yoksa 404. */
    private Schema resolveSchema(Long schemaId) {
        if (schemaId == null) {
            return schemaRepository.findByNameIgnoreCase(SchemaService.RESERVED_SCHEMA_NAME)
                    .orElseThrow(() -> new IllegalStateException(
                            "bootstrap '" + SchemaService.RESERVED_SCHEMA_NAME + "' schema row is missing"));
        }
        return schemaRepository.findById(schemaId)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_SCHEMA", "schema not found: " + schemaId, Map.of("id", String.valueOf(schemaId))));
    }

    /**
     * Var olan bir tabloyu baska bir schema'ya tasir: hem metadata (Tablo.schema) hem gercek
     * Postgres tablosu ({@code ALTER TABLE ... SET SCHEMA}) birlikte gider. Tasima islemi icin
     * {@code createTablo}'nun aksine schemaId zorunlu — "hangi schema'ya tasi" belirtilmeden bu
     * islemin bir anlami yok, o yuzden burada null'u sessizce "public"e cevirmiyoruz.
     */
    @Transactional
    public Tablo changeSchema(Long tabloId, Long newSchemaId) {
        Tablo tablo = getTablo(tabloId);
        if (newSchemaId == null) {
            throw new ValidationException("VALIDATION_MISSING_SCHEMA", "a target schema must be specified");
        }
        Schema newSchema = schemaRepository.findById(newSchemaId)
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_SCHEMA", "schema not found: " + newSchemaId, Map.of("id", String.valueOf(newSchemaId))));
        Schema currentSchema = tablo.getSchema();
        if (currentSchema.getId().equals(newSchema.getId())) {
            return tablo;
        }
        ddlExecutor.moveTableToSchema(currentSchema.getName(), tablo.getName(), newSchema.getName());
        tablo.setSchema(newSchema);
        return tablo;
    }

    /** Hem metadata'daki (Tablo.name) hem gercek Postgres tablosunun adini degistirir. */
    @Transactional
    public Tablo renameTablo(Long id, String newName) {
        Tablo tablo = getTablo(id);
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", newName);
        if (!tablo.getName().equals(newName) && tabloRepository.existsByName(newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + newName + "' already exists",
                    Map.of("name", newName));
        }
        String oldName = tablo.getName();
        tablo.setName(newName);
        ddlExecutor.renameTable(tablo.getSchema().getName(), oldName, newName);
        ddlExecutor.renamePrimaryKeyUniqueConstraintIfExists(tablo.getSchema().getName(), oldName, newName);
        return tablo;
    }

    /** Tablo silinince {@code tabloRepository.delete} JPA cascade sayesinde altindaki tum Kolon satirlarini da siler; sonra gercek tablo da drop edilir. */
    @Transactional
    public void deleteTablo(Long id) {
        Tablo tablo = getTablo(id);
        String name = tablo.getName();
        String schemaName = tablo.getSchema().getName();
        tabloRepository.delete(tablo);
        ddlExecutor.dropTable(schemaName, name);
        tablesDeletedCounter.increment();
    }

    /** Mevcut bir tabloya yeni kolon ekler; metadata + gercek {@code ALTER TABLE ADD COLUMN} birlikte gider. */
    @Transactional
    public Kolon addKolon(Long tabloId, KolonTanimi tanim) {
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
        Kolon saved = kolonRepository.save(kolon);

        ddlExecutor.addColumn(tablo.getSchema().getName(), tablo.getName(), saved.getName(), type);
        columnsCreatedCounter.increment();
        if (tanim.primaryKey()) {
            // Yeni kolon da isarete katildigi icin tum PK-isaretli kolon seti degisti:
            // eskisini kaldirip (varsa) genisletilmis setle yeniden kuruyoruz.
            syncPrimaryKeyUniqueConstraint(tablo);
        }
        return saved;
    }

    /**
     * Dogrudan {@code kolonRepository.delete(...)} cagirmiyoruz — bilerek {@code tablo.removeKolon(kolon)}
     * kullaniyoruz, cunku Kolon'u Tablo'nun kendi listesinden cikarmak, Tablo entity'sindeki
     * {@code orphanRemoval = true} sayesinde otomatik DB'den silinmesini tetikler.
     */
    @Transactional
    public void deleteKolon(Long tabloId, Long kolonId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        String columnName = kolon.getName();
        boolean wasPrimaryKey = kolon.isPrimaryKey();
        if (wasPrimaryKey) {
            // Composite unique constraint dusen kolona da bagli oldugu icin, kolonu gercekten
            // silmeden once constraint'i tamamen kaldiriyoruz (kalan PK-isaretli kolonlar varsa
            // asagida syncPrimaryKeyUniqueConstraint onlarla yeniden kurar).
            ddlExecutor.dropPrimaryKeyUniqueConstraintIfExists(tablo.getSchema().getName(), tablo.getName());
        }
        tablo.removeKolon(kolon);
        ddlExecutor.dropColumn(tablo.getSchema().getName(), tablo.getName(), columnName);
        if (wasPrimaryKey) {
            List<String> remainingPrimaryKeyColumnNames = tablo.getKolonlar().stream()
                    .filter(Kolon::isPrimaryKey)
                    .map(Kolon::getName)
                    .toList();
            if (!remainingPrimaryKeyColumnNames.isEmpty()) {
                ddlExecutor.addPrimaryKeyUniqueConstraint(
                        tablo.getSchema().getName(), tablo.getName(), remainingPrimaryKeyColumnNames);
            }
        }
    }

    /**
     * Tablonun su anki PK-isaretli kolonlarina gore gercek unique constraint'i yeniden kurar:
     * her zaman once kaldirir, sonra (bos degilse) guncel kolon setiyle tekrar ekler. Cagiran
     * taraf (addKolon/deleteKolon) sadece PK setinin degistigi anlarda cagirir.
     */
    private void syncPrimaryKeyUniqueConstraint(Tablo tablo) {
        List<String> primaryKeyColumnNames = tablo.getKolonlar().stream()
                .filter(Kolon::isPrimaryKey)
                .map(Kolon::getName)
                .toList();
        ddlExecutor.dropPrimaryKeyUniqueConstraintIfExists(tablo.getSchema().getName(), tablo.getName());
        if (!primaryKeyColumnNames.isEmpty()) {
            ddlExecutor.addPrimaryKeyUniqueConstraint(tablo.getSchema().getName(), tablo.getName(), primaryKeyColumnNames);
        }
    }

    /** Kolonun sadece adini degistirir — tipi olusturulduktan sonra hic degistirilemez (bkz. Kolon.type, updatable=false). */
    @Transactional
    public Kolon renameKolon(Long tabloId, Long kolonId, String newName) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", newName);
        if (!kolon.getName().equals(newName) && kolonRepository.existsByTabloAndName(tablo, newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + newName + "' already exists in this table",
                    Map.of("name", newName));
        }
        String oldName = kolon.getName();
        kolon.setName(newName);
        ddlExecutor.renameColumn(tablo.getSchema().getName(), tablo.getName(), oldName, newName);
        return kolon;
    }

    /** Tag'in gercek DB semasinda hic karsiligi yok (sadece metadata) — o yuzden burada ddlExecutor cagrisi yok, sade bir entity guncellemesi. */
    @Transactional
    public Kolon changeKolonTag(Long tabloId, Long kolonId, Long tagId) {
        Tablo tablo = getTablo(tabloId);
        Kolon kolon = findKolonInTablo(tablo, kolonId);
        kolon.setTag(resolveTag(tagId));
        return kolon;
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
