package dbadmin.backend.ddl;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Runs the real CREATE/ALTER/DROP TABLE statements behind the Tablo/Kolon
// metadata. JDBC placeholders (?) only work for values, never for
// identifiers (table/column names), so identifiers are validated against
// the same whitelist as NameValidator and then double-quoted before being
// concatenated into SQL - this is what keeps it safe from SQL injection
// even though the SQL text itself is hand-built.
@Component
//Component: önceden üretilen nesneyi alıp ihtiyaca göre tekrar kullanma işlemidir.
public class TableDdlExecutor {
        //hatalı giriş ve hackerşardan korunmak için yazılmıs bir metot.
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");

    private final JdbcTemplate jdbcTemplate;

    public TableDdlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ColumnDefinition(String name, ColumnType type) {
    }

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

    private String quote(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            // The service layer already validates names before reaching here (see
            // NameValidator); this is a defense-in-depth check against SQL injection,
            // not a user-facing validation path.
            throw new IllegalStateException("unsafe identifier reached the DDL layer: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
