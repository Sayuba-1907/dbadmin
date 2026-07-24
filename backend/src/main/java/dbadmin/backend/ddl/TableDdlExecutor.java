package dbadmin.backend.ddl;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tablo/Kolon metadata'sinin arkasinda gercek {@code CREATE/ALTER/DROP TABLE} SQL'ini
 * calistiran sinif — burasi metadata katmanini gercek Postgres semasiyla senkron tutan yer.
 * <p>
 * Onemli detay: JDBC'nin {@code ?} placeholder'lari sadece deger icin calisir, tablo/kolon
 * ismi gibi identifier'lar icin calismaz. O yuzden burada SQL string olarak elle birlestiriliyor
 * ({@code "CREATE TABLE " + tableName + ...}); bu normalde SQL injection riski olurdu, ama
 * {@link #quote} her identifier'i ayni whitelist regex'inden gecirip cift tirnak icine aliyor
 * ({@code "isim"}), boylece guvenli hale geliyor.
 */
@Component
public class TableDdlExecutor {

    /** {@link dbadmin.backend.validation.NameValidator} ile ayni kural: SQL'e gitmeden son bir savunma katmani. */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");

    private final JdbcTemplate jdbcTemplate;

    public TableDdlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** createTable'a verilecek tek bir kolon tanimi: isim + tip. Ayri bir class degil, kucuk bir veri tasiyici (record). */
    public record ColumnDefinition(String name, ColumnType type) {
    }

    /** Gercek {@code CREATE TABLE "isim" ("kolon1" tip1, "kolon2" tip2, ...)} calistirir. */
    public void createTable(String tableName, List<ColumnDefinition> columns) {
        String columnsSql = columns.stream()
                .map(c -> quote(c.name()) + " " + c.type().postgresType())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow(() -> new IllegalArgumentException("a table needs at least one column"));
        jdbcTemplate.execute("CREATE TABLE " + quote(tableName) + " (" + columnsSql + ")");
    }

    public void renameTable(String oldName, String newName) {
        jdbcTemplate.execute("ALTER TABLE " + quote(oldName) + " RENAME TO " + quote(newName));
    }

    /** Tablo silinince altindaki tum kolonlar da (gercek DB'de) otomatik gider — Postgres'in kendi davranisi. */
    public void dropTable(String tableName) {
        jdbcTemplate.execute("DROP TABLE " + quote(tableName));
    }

    public void addColumn(String tableName, String columnName, ColumnType type) {
        jdbcTemplate.execute("ALTER TABLE " + quote(tableName) + " ADD COLUMN "
                + quote(columnName) + " " + type.postgresType());
    }

    public void dropColumn(String tableName, String columnName) {
        jdbcTemplate.execute("ALTER TABLE " + quote(tableName) + " DROP COLUMN " + quote(columnName));
    }

    public void renameColumn(String tableName, String oldColumnName, String newColumnName) {
        jdbcTemplate.execute("ALTER TABLE " + quote(tableName) + " RENAME COLUMN "
                + quote(oldColumnName) + " TO " + quote(newColumnName));
    }

    /**
     * Bir identifier'i (tablo/kolon adi) SQL'e gomulmeden once dogrular ve cift tirnaga alir.
     * Buraya gelen isim zaten {@link dbadmin.backend.validation.NameValidator}'dan gecmis olmali;
     * bu ikinci kontrol son bir guvenlik agi (defense in depth) — kullaniciya gosterilen bir
     * validasyon hatasi degil, "buraya kadar gelmemesi gereken bir sey geldi" anlaminda
     * {@link IllegalStateException} firlatir.
     */
    private String quote(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalStateException("unsafe identifier reached the DDL layer: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
