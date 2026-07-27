package dbadmin.backend.ddl;

import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema metadata'sinin arkasinda gercek {@code CREATE/DROP SCHEMA} SQL'ini calistiran sinif —
 * {@link TableDdlExecutor} ile ayni mantik, bkz. oradaki aciklama (identifier'lar SQL string
 * olarak elle birlestiriliyor, {@link #quote} whitelist regex'iyle guvenli hale getiriyor).
 */
@Component
public class SchemaDdlExecutor {

    /** {@link dbadmin.backend.validation.NameValidator} ile ayni kural: SQL'e gitmeden son bir savunma katmani. */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");

    private final JdbcTemplate jdbcTemplate;

    public SchemaDdlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Gercek {@code CREATE SCHEMA "isim"} calistirir. */
    public void createSchema(String name) {
        jdbcTemplate.execute("CREATE SCHEMA " + quote(name));
    }

    /** {@code CASCADE}: schema silinince altindaki tum tablolar da (gercek DB'de) otomatik gider. */
    public void dropSchema(String name) {
        jdbcTemplate.execute("DROP SCHEMA " + quote(name) + " CASCADE");
    }

    private String quote(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalStateException("unsafe identifier reached the DDL layer: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
