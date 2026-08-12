package dbadmin.backend.service;

import dbadmin.backend.aop.BusinessLog;
import dbadmin.backend.ddl.ColumnType;
import dbadmin.backend.ddl.TableDdlExecutor;
import dbadmin.backend.entity.DataColumn;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.entity.User;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.repository.TagRepository;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.security.UserRoleCacheService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Is mantiginin (business logic) yasadigi yer — controller'lar burayi cagirir, burasi degil onlari.
 * Her public metod iki isi birden yapar: (1) metadata'yi ({@code DataTable}/{@code DataColumn} satirlari)
 * gunceller, (2) {@link TableDdlExecutor} uzerinden gercek Postgres semasini ayni yonde degistirir.
 * Ikisi de ayni {@code @Transactional} icinde oldugu icin biri patlarsa oburu de geri alinir,
 * boylece metadata ile gercek DB semasi hicbir zaman birbirinden kopmaz.
 */
@Service
public class TableService {

    private final TableRepository tableRepository;
    private final ColumnRepository columnRepository;
    private final TagRepository tagRepository;
    private final SchemaRepository schemaRepository;
    private final TableDdlExecutor ddlExecutor;
    private final SpanRunner spanRunner;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRoleCacheService userRoleCacheService;
    private final UserRepository userRepository;

    // Is olaylarini sayan kumulatif sayaclar (Prometheus'ta _total soneki alir) — "kac tablo
    // olusturuldu" gibi sorular icin. Anlik durum ("su an kac tablo var") icin ise Gauge
    // kullaniliyor (asagida, constructor'da kaydediliyor) cunku o hep DB'nin canli sayisini yansitir.
    private final Counter tablesCreatedCounter;
    private final Counter tablesDeletedCounter;
    private final Counter columnsCreatedCounter;

    public TableService(TableRepository tableRepository, ColumnRepository columnRepository,
            TagRepository tagRepository, SchemaRepository schemaRepository, TableDdlExecutor ddlExecutor,
            SpanRunner spanRunner, AuditLogService auditLogService, NotificationService notificationService,
            UserRoleCacheService userRoleCacheService, UserRepository userRepository,
            MeterRegistry meterRegistry) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.tagRepository = tagRepository;
        this.schemaRepository = schemaRepository;
        this.ddlExecutor = ddlExecutor;
        this.spanRunner = spanRunner;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.userRoleCacheService = userRoleCacheService;
        this.userRepository = userRepository;
        // Dikkat: isim ".created" ile bitmesin — Prometheus/OpenMetrics'te "_created" ayri bir
        // anlam tasiyan (sayacin olusturulma zaman damgasi) rezerve bir sonek; Micrometer bunu
        // fark edip "created" kelimesini sessizce siliyor (ör. "dbadmin.tables.created" ->
        // "dbadmin_tables_total" olarak cikiyor, "created" kayboluyor). "creations" cakismiyor.
        this.tablesCreatedCounter = meterRegistry.counter("dbadmin.tables.creations");
        this.tablesDeletedCounter = meterRegistry.counter("dbadmin.tables.deletions");
        this.columnsCreatedCounter = meterRegistry.counter("dbadmin.columns.creations");
        meterRegistry.gauge("dbadmin.tables.active", tableRepository, TableRepository::count);
    }
    /**
     * {@code @Transactional}: bu metod icindeki tum DB islemlerini tek bir islem paketine alir;
     * hepsi basarili olursa commit eder, biri hata verirse tumunu geri alir (rollback).
     * {@code readOnly = true} sadece okuma yapildigi icin performans ipucu — yazma islemi yapmaz.
     */
    /**
     * Iki adimli sayfalama kullanir (bkz. {@link TableRepository#findPageableIdsExcludingSchema}
     * javadoc'u): once sadece id'ler sayfalanir (LIMIT/OFFSET gercekten SQL'de calissin diye,
     * {@code columns} fetch join'i olmadan), sonra o id'lerin tam hali (kolon/tag/schema dahil)
     * TEK ek sorguda cekilir. {@code new PageImpl<>(...)} ile iki sorgunun sonucu (asil veri +
     * ilk sorgudan gelen dogru toplam sayisi) tek bir {@link Page} nesnesinde birlestirilir.
     */
    @Transactional(readOnly = true)
    public Page<DataTable> listTables(Pageable pageable) {
        Page<Long> idPage = tableRepository.findPageableIdsExcludingSchema(
                SchemaService.RESERVED_SCHEMA_NAME, pageable);
        List<DataTable> tables = tableRepository.findAllByIdInOrderByNameAsc(idPage.getContent());
        return new PageImpl<>(tables, pageable, idPage.getTotalElements());
    }

    /** Sidebar'daki schema -> tablo hiyerarsisi icin: sadece bir schema'nin altindaki tablolar, isme gore alfabetik sirali. */
    @Transactional(readOnly = true)
    public List<DataTable> listTablesBySchema(Long schemaId) {
        return tableRepository.findBySchemaIdOrderByNameAsc(schemaId);
    }

    /**
     * Id ile tek tablo bulur; yoksa 404'e cevrilecek {@link NotFoundException} firlatir (bkz.
     * GlobalExceptionHandler). Gizli schema'daki ("public") eski satirlar da bulunamamis sayilir
     * (bkz. {@link SchemaService#RESERVED_SCHEMA_NAME}) — bu metodu tum yazma islemleri de
     * kullandigi icin, oradaki bir tablo web uzerinden okunamaz da degistirilemez de.
     */
    @Transactional(readOnly = true)
    public DataTable getTable(Long id) {
        return tableRepository.findById(id)
                .filter(table -> !SchemaService.isHidden(table.getSchema()))
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_TABLE", "table not found: " + id, Map.of("id", String.valueOf(id))));
    }

    /**
     * SADECE OGRETICI/DEMO AMACLI — gercek uygulama akisinin bir parcasi degil, N+1 problemini
     * canli gostermek icin var. Bilinclidir bir anti-pattern uygular: once tabloya ait kolon
     * id'lerini TEK bir sorguyla ceker ({@link ColumnRepository#findIdsByTableId}), ardindan
     * her id icin AYRI AYRI {@code columnRepository.findById(...)} cagirir — "id listesi al,
     * sonra tek tek dongude sorgula" seklindeki klasik N+1 hatasi. N kolonlu bir tablo icin
     * toplam 1 + N sorgu uretir (ornegin 5 kolon = 6 sorgu). Dogru yaklasim tek bir
     * {@code columnRepository.findAllById(ids)} (tek bir {@code IN (...)} sorgusu) olurdu —
     * bkz. TableRepository'deki {@code @EntityGraph} yorumlari, ayni sorunun tablo/kolon
     * iliskisindeki gercek cozumu icin.
     */
    @Transactional(readOnly = true)
    public List<DataColumn> listColumnsNPlusOneDemo(Long tableId) {
        List<Long> columnIds = columnRepository.findIdsByTableId(tableId); // Sorgu 1
        return columnIds.stream()
                .map(columnId -> columnRepository.findById(columnId) // Sorgu 2..N+1: her kolon icin ayri!
                        .orElseThrow(() -> new NotFoundException(
                                "NOT_FOUND_COLUMN", "column not found: " + columnId,
                                Map.of("id", String.valueOf(columnId)))))
                .toList();
    }

    /**
     * Yeni tablo olusturur: once metadata (DataTable + DataColumn satirlari) DB'ye yazilir, sonra ayni
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
    public DataTable createTable(String name, Long schemaId, List<ColumnSpec> columnSpecs) {
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", name);
        if (tableRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + name + "' already exists",
                    Map.of("name", name));
        }
        if (columnSpecs == null || columnSpecs.isEmpty()) {
            throw new ValidationException("VALIDATION_TABLE_NEEDS_COLUMN", "a table needs at least one column");
        }
        Schema schema = resolveSchema(schemaId);

        DataTable table = new DataTable(name);
        table.setSchema(schema);
        CurrentUser creator = currentUser();
        table.setCreatedByUserId(creator != null ? creator.id() : null);
        Set<String> seenNames = new HashSet<>();
        List<TableDdlExecutor.ColumnDefinition> ddlColumns = new ArrayList<>();
        List<String> primaryKeyColumnNames = new ArrayList<>();

        for (ColumnSpec spec : columnSpecs) {
            NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", spec.name());
            if (!seenNames.add(spec.name())) {
                throw new ConflictException(
                        "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
                        "duplicate column name in request: " + spec.name(),
                        Map.of("name", spec.name()));
            }
            ColumnType type = ColumnType.fromMetadataValue(spec.type());

            DataColumn column = new DataColumn(spec.name(), type.metadataValue(), table);
            column.setTag(resolveTag(spec.tagId()));
            column.setPrimaryKey(spec.primaryKey());
            table.addColumn(column);
            ddlColumns.add(new TableDdlExecutor.ColumnDefinition(spec.name(), type));
            if (spec.primaryKey()) {
                primaryKeyColumnNames.add(spec.name());
            }
        }

        DataTable saved = spanRunner.inSpan("metadata-write", () -> tableRepository.save(table));
        // PK isaretli kolonlar CREATE TABLE'in kendi icinde veriliyor (ayri bir ALTER TABLE ile
        // degil): boylece gercek tablo tek ifadede, elle yazilmis bir
        // "CREATE TABLE ... PRIMARY KEY (col1, col2)" ile birebir ayni sekilde olusuyor.
        spanRunner.inSpan("ddl-execute", "db.table.name", saved.getName(),
                () -> ddlExecutor.createTable(schema.getName(), saved.getName(), ddlColumns, primaryKeyColumnNames));
        tablesCreatedCounter.increment();
        columnsCreatedCounter.increment(ddlColumns.size());
        auditLogService.record(OperationType.TABLE_CREATED, TargetType.TABLE, saved.getId(),
                "tablo olusturuldu: " + saved.getName() + " (schema=" + schema.getName() + ")");
        return saved;
    }

    /**
     * Aktif kullanicinin id+adini birlikte doner — tablo sahipligi (Req-2.1) ve bildirimler
     * (Req-2.2, tetikleyenin id'si sahiple, adi da mesajda kiyaslanir/gosterilir) icin.
     * {@link AuditLogService#currentUsername} ile ayni kaynaktan ({@code SecurityContextHolder})
     * okur ama burada bilerek {@code AuditLogService}'e degil dogrudan cache/repository'e
     * gidiyoruz: ikisi ayri amaclara hizmet eden bagimsiz okumalar, servisler arasi gereksiz bir
     * baglanti kurmaya gerek yok. Kimlik yoksa (pratikte olmaz, bu uclar zaten authenticated) null
     * doner.
     */
    private CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        Long id = userRoleCacheService.get(username)
                .map(UserRoleCacheService.CachedUser::id)
                .orElseGet(() -> userRepository.findByUsername(username)
                        .map(User::getId)
                        .orElse(null));
        return new CurrentUser(id, username);
    }

    /** {@link #currentUser()}'in donus tipi — id null olabilir (kullanici cache/DB'de bulunamadi), adi hep dolu. */
    private record CurrentUser(Long id, String username) {
    }

    /**
     * {@code NotificationService.create} icin aktif kullaniciyi cozup cagiran 8 mutasyon metodundaki
     * (Req-2.2) tekrari toplayan kucuk bir sarmalayici. Kimlik yoksa (pratikte olmaz) sessizce
     * atlanir — bildirim best-effort bir optimizasyon degil ama tetikleyen belirlenemiyorsa
     * (Req-3.3'un kiyaslayacagi bir id yok) hicbir sey yapilamaz.
     */
    private void notifyOwner(DataTable table, NotificationType type, String message) {
        CurrentUser triggeredBy = currentUser();
        if (triggeredBy != null) {
            notificationService.create(table, triggeredBy.id(), triggeredBy.username(), type, message);
        }
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
     * Var olan bir tabloyu baska bir schema'ya tasir: hem metadata (DataTable.schema) hem gercek
     * Postgres tablosu ({@code ALTER TABLE ... SET SCHEMA}) birlikte gider. Hedef schema zorunlu
     * ve gercek bir schema olmali: "public" gizli oldugu icin (bkz.
     * {@link SchemaService#RESERVED_SCHEMA_NAME}) oraya tasima 404 ile reddedilir.
     */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public DataTable changeSchema(Long tableId, Long newSchemaId) {
        String oldSchemaName = getTable(tableId).getSchema().getName();
        DataTable table = changeSchemaCore(tableId, newSchemaId);
        if (!oldSchemaName.equals(table.getSchema().getName())) {
            auditLogService.record(OperationType.TABLE_SCHEMA_CHANGED, TargetType.TABLE, tableId,
                    "schema degisti: " + oldSchemaName + " -> " + table.getSchema().getName());
            notifyOwner(table, NotificationType.TABLE_SCHEMA_CHANGED,
                    "\"" + table.getName() + "\" tablosunun schema'si degisti: "
                            + oldSchemaName + " -> " + table.getSchema().getName());
        }
        return table;
    }

    private DataTable changeSchemaCore(Long tableId, Long newSchemaId) {
        DataTable table = getTable(tableId);
        Schema newSchema = resolveSchema(newSchemaId);
        Schema currentSchema = table.getSchema();
        if (currentSchema.getId().equals(newSchema.getId())) {
            return table;
        }
        ddlExecutor.moveTableToSchema(currentSchema.getName(), table.getName(), newSchema.getName());
        table.setSchema(newSchema);
        table.touch();
        return table;
    }

    /** Hem metadata'daki (DataTable.name) hem gercek Postgres tablosunun adini degistirir. */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public DataTable renameTable(Long id, String newName) {
        String oldName = getTable(id).getName();
        DataTable table = renameTableCore(id, newName);
        if (!oldName.equals(table.getName())) {
            auditLogService.record(OperationType.TABLE_RENAMED, TargetType.TABLE, id,
                    "isim degisti: " + oldName + " -> " + table.getName());
            notifyOwner(table, NotificationType.TABLE_RENAMED,
                    "\"" + oldName + "\" tablosunun adi degisti: " + oldName + " -> " + table.getName());
        }
        return table;
    }

    private DataTable renameTableCore(Long id, String newName) {
        DataTable table = getTable(id);
        NameValidator.validate("table name", "VALIDATION_INVALID_TABLE_NAME", newName);
        if (table.getName().equals(newName)) {
            // Postgres "ALTER TABLE x RENAME TO x" ifadesini "relation already exists" diye
            // reddediyor — yeni isim eskiyle ayniysa DDL'e hic gitmiyoruz, zaten yapilacak bir
            // sey yok.
            return table;
        }
        if (tableRepository.existsByName(newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_TABLE_NAME",
                    "a table named '" + newName + "' already exists",
                    Map.of("name", newName));
        }
        String oldName = table.getName();
        table.setName(newName);
        table.touch();
        ddlExecutor.renameTable(table.getSchema().getName(), oldName, newName);
        ddlExecutor.renamePrimaryKeyConstraintIfExists(table.getSchema().getName(), oldName, newName);
        return table;
    }

    /** Tablo silinince {@code tableRepository.delete} JPA cascade sayesinde altindaki tum DataColumn satirlarini da siler; sonra gercek tablo da drop edilir. */
    @BusinessLog("tablo-silindi")
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public void deleteTable(Long id) {
        DataTable table = getTable(id);
        String name = table.getName();
        String schemaName = table.getSchema().getName();
        spanRunner.inSpan("metadata-write", () -> tableRepository.delete(table));
        spanRunner.inSpan("ddl-execute", "db.table.name", name, () -> ddlExecutor.dropTable(schemaName, name));
        tablesDeletedCounter.increment();
        auditLogService.record(OperationType.TABLE_DELETED, TargetType.TABLE, id, "tablo silindi: " + name);
        notifyOwner(table, NotificationType.TABLE_DELETED, "\"" + name + "\" tablosu silindi.");
    }

    /** Mevcut bir tabloya yeni kolon ekler; metadata + gercek {@code ALTER TABLE ADD COLUMN} birlikte gider. */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public DataColumn addColumn(Long tableId, ColumnSpec spec) {
        DataColumn column = addColumnCore(tableId, spec);
        auditLogService.record(OperationType.COLUMN_ADDED, TargetType.COLUMN, column.getId(),
                "kolon eklendi: " + column.getName() + " (tablo=" + tableId + ")");
        notifyOwner(column.getTable(), NotificationType.COLUMN_ADDED,
                "\"" + column.getTable().getName() + "\" tablosuna \"" + column.getName() + "\" kolonu eklendi.");
        return column;
    }

    private DataColumn addColumnCore(Long tableId, ColumnSpec spec) {
        DataTable table = getTable(tableId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", spec.name());
        if (columnRepository.existsByTableAndName(table, spec.name())) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + spec.name() + "' already exists in this table",
                    Map.of("name", spec.name()));
        }
        ColumnType type = ColumnType.fromMetadataValue(spec.type());
        DataColumn column = new DataColumn(spec.name(), type.metadataValue(), table);
        column.setTag(resolveTag(spec.tagId()));
        column.setPrimaryKey(spec.primaryKey());
        table.addColumn(column);
        table.touch();
        DataColumn saved = columnRepository.save(column);

        ddlExecutor.addColumn(table.getSchema().getName(), table.getName(), saved.getName(), type);
        columnsCreatedCounter.increment();
        if (spec.primaryKey()) {
            // Yeni kolon da isarete katildigi icin tum PK-isaretli kolon seti degisti: bir
            // tablonun tek bir primary key'i olabildigi icin eskisini kaldirip genisletilmis
            // setle (composite olarak) yeniden kuruyoruz.
            syncPrimaryKeyConstraint(table);
        }
        return saved;
    }

    /**
     * Dogrudan {@code columnRepository.delete(...)} cagirmiyoruz — bilerek {@code table.removeColumn(column)}
     * kullaniyoruz, cunku DataColumn'u DataTable'in kendi listesinden cikarmak, DataTable entity'sindeki
     * {@code orphanRemoval = true} sayesinde otomatik DB'den silinmesini tetikler.
     */
    @CacheEvict(cacheNames = "schemaWorkspace", allEntries = true)
    @Transactional
    public void deleteColumn(Long tableId, Long columnId) {
        DataTable table = getTable(tableId);
        String columnName = findColumnInTable(table, columnId).getName();
        deleteColumnCore(tableId, columnId);
        auditLogService.record(OperationType.COLUMN_DELETED, TargetType.COLUMN, columnId,
                "kolon silindi: " + columnName + " (tablo=" + tableId + ")");
        notifyOwner(table, NotificationType.COLUMN_DELETED,
                "\"" + table.getName() + "\" tablosundan \"" + columnName + "\" kolonu silindi.");
    }

    private void deleteColumnCore(Long tableId, Long columnId) {
        DataTable table = getTable(tableId);
        DataColumn column = findColumnInTable(table, columnId);
        String columnName = column.getName();
        boolean wasPrimaryKey = column.isPrimaryKey();
        if (wasPrimaryKey) {
            // Composite primary key dusen kolona da bagli oldugu icin, kolonu gercekten silmeden
            // once constraint'i tamamen kaldiriyoruz (kalan PK-isaretli kolonlar varsa asagida
            // yeniden kuruluyor).
            ddlExecutor.dropPrimaryKeyConstraintIfExists(table.getSchema().getName(), table.getName());
        }
        table.removeColumn(column);
        table.touch();
        ddlExecutor.dropColumn(table.getSchema().getName(), table.getName(), columnName);
        if (wasPrimaryKey) {
            List<String> remainingPrimaryKeyColumnNames = table.getColumns().stream()
                    .filter(DataColumn::isPrimaryKey)
                    .map(DataColumn::getName)
                    .toList();
            if (!remainingPrimaryKeyColumnNames.isEmpty()) {
                ddlExecutor.addPrimaryKeyConstraint(
                        table.getSchema().getName(), table.getName(), remainingPrimaryKeyColumnNames);
            }
        }
    }

    /**
     * Tablonun su anki PK-isaretli kolonlarina gore gercek {@code PRIMARY KEY} constraint'ini
     * yeniden kurar: her zaman once kaldirir, sonra (bos degilse) guncel kolon setiyle tekrar
     * ekler. Bir tablonun birden fazla primary key'i olamadigi icin "genislet" diye bir islem
     * yok — dogru yol hep drop + yeniden add. Cagiran taraf (addColumn/deleteColumn) sadece PK
     * setinin degistigi anlarda cagirir.
     */
    private void syncPrimaryKeyConstraint(DataTable table) {
        List<String> primaryKeyColumnNames = table.getColumns().stream()
                .filter(DataColumn::isPrimaryKey)
                .map(DataColumn::getName)
                .toList();
        ddlExecutor.dropPrimaryKeyConstraintIfExists(table.getSchema().getName(), table.getName());
        if (!primaryKeyColumnNames.isEmpty()) {
            ddlExecutor.addPrimaryKeyConstraint(table.getSchema().getName(), table.getName(), primaryKeyColumnNames);
        }
    }

    /** Kolonun sadece adini degistirir — tipi olusturulduktan sonra hic degistirilemez (bkz. DataColumn.type, updatable=false). */
    @Transactional
    public DataColumn renameColumn(Long tableId, Long columnId, String newName) {
        String oldName = findColumnInTable(getTable(tableId), columnId).getName();
        DataColumn column = renameColumnCore(tableId, columnId, newName);
        if (!oldName.equals(column.getName())) {
            auditLogService.record(OperationType.COLUMN_RENAMED, TargetType.COLUMN, columnId,
                    "kolon adi degisti: " + oldName + " -> " + column.getName());
            notifyOwner(column.getTable(), NotificationType.COLUMN_RENAMED,
                    "\"" + column.getTable().getName() + "\" tablosunda kolon adi degisti: "
                            + oldName + " -> " + column.getName());
        }
        return column;
    }

    private DataColumn renameColumnCore(Long tableId, Long columnId, String newName) {
        DataTable table = getTable(tableId);
        DataColumn column = findColumnInTable(table, columnId);
        NameValidator.validate("column name", "VALIDATION_INVALID_COLUMN_NAME", newName);
        if (column.getName().equals(newName)) {
            // Postgres "ALTER TABLE ... RENAME COLUMN x TO x" ifadesini "column already exists"
            // diye reddediyor — yeni isim eskiyle ayniysa DDL'e hic gitmiyoruz.
            return column;
        }
        if (columnRepository.existsByTableAndName(table, newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_COLUMN_NAME",
                    "a column named '" + newName + "' already exists in this table",
                    Map.of("name", newName));
        }
        String oldName = column.getName();
        column.setName(newName);
        table.touch();
        ddlExecutor.renameColumn(table.getSchema().getName(), table.getName(), oldName, newName);
        return column;
    }

    /**
     * Var olan bir kolonun birincil anahtar isaretini degistirir ve tablonun gercek
     * {@code PRIMARY KEY} constraint'ini yeni isaretli kolon setiyle yeniden kurar.
     * <p>
     * Bu uc, {@code primaryKey}'in gercek bir PRIMARY KEY'e donusmesiyle birlikte gerekli hale
     * geldi: bayrak eskiden sadece kozmetikti ve yalnizca kolon olusturulurken (createTable /
     * addColumn) set edilebiliyordu, dolayisiyla var olan bir kolonu PK yapmanin tek yolu onu
     * silip yeniden eklemekti — yani icindeki veriyi kaybetmekti.
     * <p>
     * Bilinen sinir: tabloda satir varken NULL iceren bir kolonu PK yapmak Postgres tarafindan
     * reddedilir (PK kolonlari otomatik {@code NOT NULL} olur); ayni sekilde tekrar eden degerler
     * varsa da reddedilir. Her iki durumda da transaction geri alindigi icin metadata'daki isaret
     * de degismez, iki katman ayrisamaz.
     */
    @Transactional
    public DataColumn changeColumnPrimaryKey(Long tableId, Long columnId, boolean primaryKey) {
        boolean oldValue = findColumnInTable(getTable(tableId), columnId).isPrimaryKey();
        DataColumn column = changeColumnPrimaryKeyCore(tableId, columnId, primaryKey);
        if (oldValue != column.isPrimaryKey()) {
            auditLogService.record(OperationType.COLUMN_PRIMARY_KEY_CHANGED, TargetType.COLUMN, columnId,
                    "primary key " + (column.isPrimaryKey() ? "eklendi" : "kaldirildi") + ": " + column.getName());
            notifyOwner(column.getTable(), NotificationType.COLUMN_PRIMARY_KEY_CHANGED,
                    "\"" + column.getTable().getName() + "\" tablosunda \"" + column.getName() + "\" kolonunun "
                            + "primary key durumu " + (column.isPrimaryKey() ? "eklendi" : "kaldirildi") + ".");
        }
        return column;
    }

    private DataColumn changeColumnPrimaryKeyCore(Long tableId, Long columnId, boolean primaryKey) {
        DataTable table = getTable(tableId);
        DataColumn column = findColumnInTable(table, columnId);
        if (column.isPrimaryKey() == primaryKey) {
            // Isaret zaten istenen durumda: gereksiz yere DROP + ADD CONSTRAINT calistirmiyoruz.
            return column;
        }
        column.setPrimaryKey(primaryKey);
        table.touch();
        syncPrimaryKeyConstraint(table);
        return column;
    }

    /** Tag'in gercek DB semasinda hic karsiligi yok (sadece metadata) — o yuzden burada ddlExecutor cagrisi yok, sade bir entity guncellemesi. */
    @Transactional
    public DataColumn changeColumnTag(Long tableId, Long columnId, Long tagId) {
        DataColumn column = changeColumnTagCore(tableId, columnId, tagId);
        auditLogService.record(OperationType.COLUMN_TAG_CHANGED, TargetType.COLUMN, columnId,
                "tag degisti: kolon=" + column.getName() + " tagId=" + tagId);
        notifyOwner(column.getTable(), NotificationType.COLUMN_TAG_CHANGED,
                "\"" + column.getTable().getName() + "\" tablosunda \"" + column.getName() + "\" kolonunun tag'i degisti.");
        return column;
    }

    private DataColumn changeColumnTagCore(Long tableId, Long columnId, Long tagId) {
        DataTable table = getTable(tableId);
        DataColumn column = findColumnInTable(table, columnId);
        column.setTag(resolveTag(tagId));
        table.touch();
        return column;
    }

    /**
     * "Kaydet'e basinca hepsi birden gitsin" akisinin karsiligi: bir tablo uzerinde biriktirilmis
     * TUM degisiklikleri (isim, schema, silinen/eklenen/guncellenen kolonlar) tek seferde uygular.
     * <p>
     * Dikkat cekici implementasyon detayi: burada DDL'i yeniden yazmiyoruz, sadece yukaridaki
     * (zaten test edilmis) metodlarin audit'siz *Core varyantlarini ({@code renameTableCore}/
     * {@code changeSchemaCore}/{@code deleteColumnCore}/{@code renameColumnCore}/
     * {@code changeColumnTagCore}/{@code changeColumnPrimaryKeyCore}/{@code addColumnCore}) sirayla
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
    public DataTable applyChanges(Long tableId, String newName, Long newSchemaId,
            List<Long> columnIdsToDelete, List<ColumnSpec> columnsToAdd,
            List<ColumnUpdate> columnsToUpdate) {
        // Alt-islemler icin *Core metodlar cagriliyor (public renameTable/addColumn/... degil) —
        // her biri kendi audit satirini yazsaydi, tek bir "Kaydet" tiklamasi N adet audit satirina
        // bolunurdu. Bunun yerine burada tek bir ozet toplanip en sonda TEK satir yaziliyor.
        List<String> changes = new ArrayList<>();
        if (newName != null) {
            String oldName = getTable(tableId).getName();
            DataTable after = renameTableCore(tableId, newName);
            if (!oldName.equals(after.getName())) {
                changes.add("isim: " + oldName + " -> " + after.getName());
            }
        }
        if (newSchemaId != null) {
            String oldSchema = getTable(tableId).getSchema().getName();
            DataTable after = changeSchemaCore(tableId, newSchemaId);
            if (!oldSchema.equals(after.getSchema().getName())) {
                changes.add("schema: " + oldSchema + " -> " + after.getSchema().getName());
            }
        }
        for (Long columnId : columnIdsToDelete) {
            String columnName = findColumnInTable(getTable(tableId), columnId).getName();
            deleteColumnCore(tableId, columnId);
            changes.add("kolon silindi: " + columnName);
        }
        for (ColumnUpdate update : columnsToUpdate) {
            renameColumnCore(tableId, update.columnId(), update.newName());
            changeColumnTagCore(tableId, update.columnId(), update.newTagId());
            changeColumnPrimaryKeyCore(tableId, update.columnId(), update.newPrimaryKey());
            changes.add("kolon guncellendi: id=" + update.columnId());
        }
        for (ColumnSpec spec : columnsToAdd) {
            DataColumn added = addColumnCore(tableId, spec);
            changes.add("kolon eklendi: " + added.getName());
        }
        DataTable resultTable = getTable(tableId);
        if (!changes.isEmpty()) {
            auditLogService.record(OperationType.TABLE_UPDATED, TargetType.TABLE, tableId,
                    String.join("; ", changes));
            // Gercek arayuzdeki "Kaydet" butonu HEP bu metodu kullanir (bkz. Dashboard.handleSaveDraft
            // -> useTables.applyChanges) — addColumn/deleteColumn/... gibi tekil uclara eklenen
            // notifyOwner() cagrilari pratikte neredeyse hic tetiklenmez, gercek dunyadaki tum
            // duzenlemeler buradan gecer. Audit log'daki "N alt-islem -> TEK ozet satiri" ile ayni
            // gerekce: sahibe de N ayri bildirim degil, TEK bir ozet bildirimi gider.
            notifyOwner(resultTable, NotificationType.TABLE_UPDATED,
                    "\"" + resultTable.getName() + "\" tablosu güncellendi: " + String.join("; ", changes));
        }
        return resultTable;
    }

    /** Kolonu, tablonun zaten yuklu {@code columns} listesi icinde arar (ekstra sorgu atmadan) — kolon bu tabloya ait degilse 404 doner. */
    private DataColumn findColumnInTable(DataTable table, Long columnId) {
        return table.getColumns().stream()
                .filter(c -> c.getId().equals(columnId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_COLUMN",
                        "column not found in this table: " + columnId,
                        Map.of("id", String.valueOf(columnId))));
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
