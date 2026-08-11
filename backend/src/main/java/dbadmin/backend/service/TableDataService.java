package dbadmin.backend.service;

import dbadmin.backend.ddl.ColumnType;
import dbadmin.backend.dto.TableDataResponse;
import dbadmin.backend.entity.DataColumn;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gercek Postgres tablosunun SATIR verisini okur (requirement notu 7 — "DBeaver'daki gibi Show
 * Data"). {@link TableService} ve {@code ddl/} paketinden bilerek AYRI: onlar metadata + DDL
 * (yapiyi degistirme), burasi sadece {@code SELECT} (yapiya hic dokunmuyor, salt okunur).
 * <p>
 * Sema/tablo adi burada da (DDL executor'lardaki gibi) SQL metnine string concatenation ile
 * giriyor — JDBC parametreleri identifier'lar icin kullanilamaz, sadece degerler icin (LIMIT/OFFSET).
 * Guvenlik {@link #quote} ile ayni whitelist kontrolune dayanir (bkz. TableDdlExecutor'daki
 * kopyasi) — burada tekrarlanmasinin sebebi, isimlerin zaten TableService uzerinden gelip
 * NameValidator'dan gecmis olsa da, bu sinifin DDL executor'lara bagimli olmadan kendi basina
 * guvenli kalmasini istememiz (savunma katmanlari birbirine bagimli olmamali).
 */
@Service
public class TableDataService {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");
    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(20, 50, 100, 200, 500);
    private static final DateTimeFormatter CSV_KEY_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    private final TableService tableService;
    private final JdbcTemplate jdbcTemplate;
    private final MinioService minioService;

    public TableDataService(TableService tableService, JdbcTemplate jdbcTemplate, MinioService minioService) {
        this.tableService = tableService;
        this.jdbcTemplate = jdbcTemplate;
        this.minioService = minioService;
    }

    @Transactional(readOnly = true)
    public TableDataResponse getData(Long tableId, int page, int size) {
        if (page < 0) {
            throw new ValidationException("VALIDATION_INVALID_PAGE", "page must be 0 or greater");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new ValidationException(
                    "VALIDATION_INVALID_PAGE_SIZE",
                    "size must be one of " + ALLOWED_PAGE_SIZES,
                    Map.of("allowed", ALLOWED_PAGE_SIZES.toString()));
        }

        // getTable zaten gizli 'public' semasini disliyor (bkz. TableService.getTable) — bu
        // yuzden uygulamanin kendi metadata tablolarina (tablo/kolon/sema/tag) bu ucla asla
        // erisilemez, sadece kullanicinin olusturdugu gercek tablolara.
        DataTable table = tableService.getTable(tableId);
        String qualifiedName = quote(table.getSchema().getName()) + "." + quote(table.getName());

        long totalRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + qualifiedName, Long.class);

        // PRIMARY KEY'e gore sirali: PK yoksa Postgres'in kendi (garanti edilmeyen) fiziksel
        // sirasina dusulur. Bunun asil sebebi sayfalamanin KARARLI olmasi (ayni sayfayi iki kez
        // istersen ayni satirlari getirmesi) — ama yan etkisi olarak yeni eklenen bir satir da
        // (bkz. insertRow) hep AYNI sayfada (genelde son sayfada) cikar, TableDetail.tsx bu
        // yuzden ekleme basarili olunca son sayfaya atlar.
        String orderByClause = orderByClause(table);

        RawPage rawPage = jdbcTemplate.query(
                "SELECT * FROM " + qualifiedName + orderByClause + " LIMIT ? OFFSET ?",
                extractor(),
                size, page * size);

        return new TableDataResponse(rawPage.columns(), rawPage.rows(), totalRows);
    }

    private String orderByClause(DataTable table) {
        String pkColumns = table.getColumns().stream()
                .filter(DataColumn::isPrimaryKey)
                .map(c -> quote(c.getName()))
                .collect(Collectors.joining(","));
        return pkColumns.isEmpty() ? "" : " ORDER BY " + pkColumns;
    }

    /**
     * Yeni bir satir ekler (requirement notu 7'nin devami — kullanicinin kendi veri girmesi).
     * Sadece tabloda GERCEKTEN var olan kolonlar kabul edilir (metadata'ya karsi dogrulanir);
     * her deger, o kolonun gercek Postgres tipine (bkz. {@link ColumnType#postgresType()})
     * ACIKCA cast edilerek baglanir — JDBC'nin parametre tipini kendi kendine tahmin etmesine
     * (ör. bir timestamp kolonuna duz metin baglarken) guvenilmez, driver'in reddetme riski olur.
     */
    @Transactional
    public void insertRow(Long tableId, Map<String, Object> values) {
        DataTable table = tableService.getTable(tableId);
        Map<String, DataColumn> columnsByName = table.getColumns().stream()
                .collect(Collectors.toMap(DataColumn::getName, c -> c));

        for (String key : values.keySet()) {
            if (!columnsByName.containsKey(key)) {
                throw new ValidationException(
                        "VALIDATION_UNKNOWN_COLUMN", "unknown column: " + key, Map.of("column", key));
            }
        }
        if (values.isEmpty()) {
            throw new ValidationException("VALIDATION_EMPTY_ROW", "at least one column value must be provided");
        }

        List<String> columnNames = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (DataColumn column : table.getColumns()) {
            if (!values.containsKey(column.getName())) {
                continue;
            }
            columnNames.add(quote(column.getName()));
            placeholders.add("?::" + ColumnType.fromMetadataValue(column.getType()).postgresType());
            args.add(values.get(column.getName()));
        }

        String qualifiedName = quote(table.getSchema().getName()) + "." + quote(table.getName());
        String sql = "INSERT INTO " + qualifiedName + " (" + String.join(",", columnNames) + ") VALUES ("
                + String.join(",", placeholders) + ")";
        jdbcTemplate.update(sql, args.toArray());
    }

    /**
     * Var olan bir satiri gunceller — kullanicinin "Show Data"da satir duzenlemesi ihtiyaci
     * (requirement notu 7'nin devami). Satir, PRIMARY KEY ile bulunur; bu yuzden PK'siz bir
     * tabloda tekil satir guncellenemez (birden fazla ozdes satir varsa hangisinin
     * kastedildigi belirsiz kalirdi) — {@code VALIDATION_NO_PRIMARY_KEY} firlatilir. PK
     * kolonlari {@code values} icinde DEGISTIRILEMEZ (satirin kimligini WHERE kismindan farkli
     * bir sey yapardi); degistirilmek istenirse {@code VALIDATION_PRIMARY_KEY_READONLY}.
     */
    @Transactional
    public void updateRow(Long tableId, Map<String, Object> pk, Map<String, Object> values) {
        DataTable table = tableService.getTable(tableId);
        Map<String, DataColumn> columnsByName = table.getColumns().stream()
                .collect(Collectors.toMap(DataColumn::getName, c -> c));

        List<String> pkColumnNames = table.getColumns().stream()
                .filter(DataColumn::isPrimaryKey)
                .map(DataColumn::getName)
                .toList();
        if (pkColumnNames.isEmpty()) {
            throw new ValidationException(
                    "VALIDATION_NO_PRIMARY_KEY", "table has no primary key, rows cannot be edited individually");
        }
        if (!pk.keySet().equals(Set.copyOf(pkColumnNames))) {
            throw new ValidationException(
                    "VALIDATION_MISSING_PRIMARY_KEY_VALUE",
                    "pk must contain exactly the table's primary key columns: " + pkColumnNames,
                    Map.of("expected", pkColumnNames.toString()));
        }

        for (String key : values.keySet()) {
            if (!columnsByName.containsKey(key)) {
                throw new ValidationException(
                        "VALIDATION_UNKNOWN_COLUMN", "unknown column: " + key, Map.of("column", key));
            }
            if (pkColumnNames.contains(key)) {
                throw new ValidationException(
                        "VALIDATION_PRIMARY_KEY_READONLY",
                        "primary key columns cannot be edited: " + key,
                        Map.of("column", key));
            }
        }
        if (values.isEmpty()) {
            throw new ValidationException("VALIDATION_EMPTY_ROW", "at least one column value must be provided");
        }

        List<String> setClauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (DataColumn column : table.getColumns()) {
            if (!values.containsKey(column.getName())) {
                continue;
            }
            setClauses.add(
                    quote(column.getName()) + " = ?::" + ColumnType.fromMetadataValue(column.getType()).postgresType());
            args.add(values.get(column.getName()));
        }

        List<String> whereClauses = new ArrayList<>();
        for (String pkColumnName : pkColumnNames) {
            DataColumn column = columnsByName.get(pkColumnName);
            whereClauses.add(
                    quote(pkColumnName) + " = ?::" + ColumnType.fromMetadataValue(column.getType()).postgresType());
            args.add(pk.get(pkColumnName));
        }

        String qualifiedName = quote(table.getSchema().getName()) + "." + quote(table.getName());
        String sql = "UPDATE " + qualifiedName + " SET " + String.join(",", setClauses) + " WHERE "
                + String.join(" AND ", whereClauses);
        int updatedRows = jdbcTemplate.update(sql, args.toArray());
        if (updatedRows == 0) {
            throw new NotFoundException("NOT_FOUND_ROW", "no row matches the given primary key");
        }
    }

    /**
     * Requirement notu 8 ("CSV Export ekle -> minio'ya yazilacak"). {@link #getData} gibi
     * sayfalanmis DEGIL — export'un anlami butun tabloyu tek dosyada vermek, whitelist'teki en
     * buyuk boyut (500) burada uygulanmaz. Uretilen CSV, denetim/izlenebilirlik icin MinIO'ya
     * yazilir (bkz. {@link MinioService#upload}, {@code AuditLogBackupService} ile ayni desen);
     * ayni cagrida donen byte[] {@code TableController} tarafindan tarayiciya da indirilir.
     */
    @Transactional(readOnly = true)
    public CsvExportResult exportCsv(Long tableId) {
        DataTable table = tableService.getTable(tableId);
        String qualifiedName = quote(table.getSchema().getName()) + "." + quote(table.getName());
        String orderByClause = orderByClause(table);

        RawPage rawPage = jdbcTemplate.query("SELECT * FROM " + qualifiedName + orderByClause, extractor());
        byte[] content = buildCsv(rawPage);

        Instant exportedAt = Instant.now();
        String baseName = table.getSchema().getName() + "_" + table.getName();
        String key = "csv-exports/" + baseName + "-" + CSV_KEY_TIMESTAMP.format(exportedAt) + ".csv";
        minioService.upload(key, content, "text/csv");

        return new CsvExportResult(key, baseName + ".csv", content, rawPage.rows().size());
    }

    private byte[] buildCsv(RawPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append(page.columns().stream().map(this::csvEscape).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : page.rows()) {
            sb.append(page.columns().stream()
                            .map(col -> csvEscape(row.get(col) == null ? "" : String.valueOf(row.get(col))))
                            .collect(Collectors.joining(",")))
                    .append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private ResultSetExtractor<RawPage> extractor() {
        return (ResultSet rs) -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), toJsonSafeValue(rs.getObject(i)));
                }
                rows.add(row);
            }
            return new RawPage(columns, rows);
        };
    }

    /**
     * String/Number/Boolean disindaki her sey (UUID, java.sql.Date/Timestamp/Time, jsonb'nin
     * surucuye ozel PGobject'i, diziler, vs.) Jackson'in nasil serilestirecegini garanti
     * edemeyecegimiz turler — DBeaver'in de yaptigi gibi hepsini metne ceviriyoruz. Amac tam
     * tip sadakati degil, veriyi GORUNTULEMEK (salt okunur bir liste, duzenleme yok).
     */
    private Object toJsonSafeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Timestamp || value instanceof java.sql.Date || value instanceof Time) {
            return value.toString();
        }
        return value.toString();
    }

    /** DDL executor'lardaki (ör. TableDdlExecutor#quote) ayni whitelist kontrolunun bagimsiz bir kopyasi. */
    private String quote(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalStateException("unsafe identifier reached the data-read layer: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private record RawPage(List<String> columns, List<Map<String, Object>> rows) {
    }

    public record CsvExportResult(String key, String fileName, byte[] content, int rowCount) {
    }
}
