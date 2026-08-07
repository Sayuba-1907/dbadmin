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

    /**
     * Uretilen (kullanici girdisi olmayan) identifier'lar icin daha gevsek bir desen: tablo
     * adindan turetildigi icin {@link #IDENTIFIER_PATTERN}'in 30 karakter siniri yetmeyebilir
     * (ör. "_pkey" eki), ama Postgres'in kendi identifier siniri (63 byte) hala gecerli.
     */
    private static final Pattern GENERATED_IDENTIFIER_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{0,62}$");

    private final JdbcTemplate jdbcTemplate;

    public TableDdlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** createTable'a verilecek tek bir kolon tanimi: isim + tip. Ayri bir class degil, kucuk bir veri tasiyici (record). */
    public record ColumnDefinition(String name, ColumnType type) {
    }

    /**
     * Gercek {@code CREATE TABLE "schema"."isim" ("kolon1" tip1, ..., CONSTRAINT "isim_pkey"
     * PRIMARY KEY ("kolonA", "kolonB"))} calistirir.
     * <p>
     * Tabloda sadece kullanicinin tanimladigi kolonlar bulunur — eskiden her tabloya otomatik
     * eklenen bir {@code "id"} kolonu vardi, artik yok. {@code primaryKeyColumnNames} bos
     * gelirse tablo primary key'siz olusturulur (Postgres buna izin verir); birden fazla isim
     * gelirse tek bir composite (bilesik) primary key kurulur — istenen cikti tam olarak
     * {@code PRIMARY KEY (col1, col2)} seklindedir.
     */
    public void createTable(String schemaName, String tableName, List<ColumnDefinition> columns,
            List<String> primaryKeyColumnNames) {
        String columnsSql = columns.stream()
                .map(c -> quote(c.name()) + " " + c.type().postgresType())
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow(() -> new IllegalArgumentException("a table needs at least one column"));
        String primaryKeySql = primaryKeyColumnNames.isEmpty()
                ? ""
                : ", CONSTRAINT " + quoteGenerated(primaryKeyConstraintName(tableName)) + " PRIMARY KEY ("
                        + joinQuoted(primaryKeyColumnNames) + ")";
        jdbcTemplate.execute(
                "CREATE TABLE " + qualifiedName(schemaName, tableName) + " (" + columnsSql + primaryKeySql + ")");
    }

    /** Tablo bir schema'dan digerine tasinmaz, sadece adi degisir — bu yuzden tek bir schemaName yeterli. */
    public void renameTable(String schemaName, String oldName, String newName) {
        jdbcTemplate.execute(
                "ALTER TABLE " + qualifiedName(schemaName, oldName) + " RENAME TO " + quote(newName));
    }

    /** Tablo silinince altindaki tum kolonlar da (gercek DB'de) otomatik gider — Postgres'in kendi davranisi. */
    public void dropTable(String schemaName, String tableName) {
        jdbcTemplate.execute("DROP TABLE " + qualifiedName(schemaName, tableName));
    }

    public void addColumn(String schemaName, String tableName, String columnName, ColumnType type) {
        jdbcTemplate.execute("ALTER TABLE " + qualifiedName(schemaName, tableName) + " ADD COLUMN "
                + quote(columnName) + " " + type.postgresType());
    }

    public void dropColumn(String schemaName, String tableName, String columnName) {
        jdbcTemplate.execute(
                "ALTER TABLE " + qualifiedName(schemaName, tableName) + " DROP COLUMN " + quote(columnName));
    }

    public void renameColumn(String schemaName, String tableName, String oldColumnName, String newColumnName) {
        jdbcTemplate.execute("ALTER TABLE " + qualifiedName(schemaName, tableName) + " RENAME COLUMN "
                + quote(oldColumnName) + " TO " + quote(newColumnName));
    }

    /**
     * Kullanicinin "birincil anahtar" diye isaretledigi kolonlar uzerine var olan bir tabloda
     * gercek {@code PRIMARY KEY} constraint'i kurar. Tek kolon verilirse basit, birden fazla
     * verilirse composite primary key olur — ikisi de ayni ifadeyle kurulur. Cagiran taraf once
     * {@link #dropPrimaryKeyConstraintIfExists} ile eskisini kaldirmis olmali; bir tablonun
     * yalnizca tek bir primary key'i olabilir, yoksa "multiple primary keys" hatasi alinir.
     * <p>
     * Postgres {@code ADD PRIMARY KEY} sirasinda ilgili kolonlari otomatik {@code NOT NULL}
     * yapar; tabloda o kolonu bos olan satirlar varsa ifade hata verir (dolayisiyla transaction
     * geri alinir ve metadata da yazilmaz).
     */
    public void addPrimaryKeyConstraint(String schemaName, String tableName, List<String> columnNames) {
        if (columnNames.isEmpty()) {
            throw new IllegalArgumentException("a primary key needs at least one column");
        }
        jdbcTemplate.execute("ALTER TABLE " + qualifiedName(schemaName, tableName) + " ADD CONSTRAINT "
                + quoteGenerated(primaryKeyConstraintName(tableName)) + " PRIMARY KEY (" + joinQuoted(columnNames) + ")");
    }

    /** Yoksa sessizce hicbir sey yapmaz ({@code IF EXISTS}) — cagiran taraf her seferinde once bunu calistirip sonra gerekiyorsa yeniden ekler. */
    public void dropPrimaryKeyConstraintIfExists(String schemaName, String tableName) {
        jdbcTemplate.execute("ALTER TABLE " + qualifiedName(schemaName, tableName) + " DROP CONSTRAINT IF EXISTS "
                + quoteGenerated(primaryKeyConstraintName(tableName)));
    }

    /**
     * Tablo yeniden adlandirilinca (bkz. {@link #renameTable}) constraint ismi de tablo adiyla
     * senkron kalsin diye cagrilir — yoksa bir sonraki {@code addPrimaryKeyConstraint}/
     * {@code dropPrimaryKeyConstraintIfExists} cagrisi yeni isimden hesapladigi constraint
     * ismini bulamaz, eski constraint sahipsiz kalir. Constraint yoksa (hic PK isaretli kolon
     * yoktuysa) sessizce hicbir sey yapmaz — Postgres'te {@code RENAME CONSTRAINT}'in
     * {@code IF EXISTS} varyanti olmadigi icin varligini once kendimiz kontrol ediyoruz.
     */
    public void renamePrimaryKeyConstraintIfExists(String schemaName, String oldTableName, String newTableName) {
        String oldConstraintName = primaryKeyConstraintName(oldTableName);
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.table_constraints "
                        + "WHERE table_schema = ? AND table_name = ? AND constraint_name = ?)",
                Boolean.class, schemaName, newTableName, oldConstraintName);
        if (!Boolean.TRUE.equals(exists)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + qualifiedName(schemaName, newTableName) + " RENAME CONSTRAINT "
                + quoteGenerated(oldConstraintName) + " TO " + quoteGenerated(primaryKeyConstraintName(newTableName)));
    }

    /**
     * Constraint (ve arkasindaki index'in) ismini tablo adindan turetir. Bunu bilerek sabit bir
     * isim yerine kullaniyoruz: Postgres'te constraint'in arkasindaki index schema genelinde
     * benzersiz olmak zorunda (tablo bazinda degil) — sabit bir isim ayni schema'daki ikinci
     * tabloda "relation already exists" hatasi verirdi. Tablo adlari uygulama genelinde zaten
     * benzersiz oldugu icin (bkz. TableService#createTable, existsByName) bu turetilen isim de
     * otomatik olarak benzersiz olur.
     * <p>
     * {@code _pkey} soneki Postgres'in primary key'lere kendi verdigi varsayilan isimle ayni —
     * boylece DBeaver'da tablo "elle yazilmis" bir {@code CREATE TABLE}'dan ayirt edilemez gorunur.
     */
    private String primaryKeyConstraintName(String tableName) {
        return tableName + "_pkey";
    }

    /** Kolon isimlerini tek tek {@link #quote}'dan gecirip {@code "a", "b"} seklinde birlestirir. */
    private String joinQuoted(List<String> columnNames) {
        return columnNames.stream()
                .map(this::quote)
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow(() -> new IllegalArgumentException("at least one column name is required"));
    }

    /** Tabloyu bir schema'dan digerine tasir; {@code currentSchemaName} tablonun su anki (eski) schema'sidir. */
    public void moveTableToSchema(String currentSchemaName, String tableName, String newSchemaName) {
        jdbcTemplate.execute(
                "ALTER TABLE " + qualifiedName(currentSchemaName, tableName) + " SET SCHEMA " + quote(newSchemaName));
    }

    /**
     * "public" disindaki schema'lardaki tablolar Postgres'in varsayilan {@code search_path}'inde
     * yer almaz — o yuzden her DDL'de tabloyu {@code "schema"."tablo"} seklinde tam nitelikli
     * (fully qualified) yaziyoruz; sadece tablo adini yazmak "public" disinda bir schema'daki
     * tabloyu bulamaz.
     */
    private String qualifiedName(String schemaName, String tableName) {
        return quote(schemaName) + "." + quote(tableName);
    }

    /**
     * Bir identifier'i (schema/tablo/kolon adi) SQL'e gomulmeden once dogrular ve cift tirnaga alir.
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

    /** {@link #quote} ile ayni amac, ama uretilen (tablo adindan turetilen) identifier'lar icin gevsetilmis uzunluk siniriyla ({@link #GENERATED_IDENTIFIER_PATTERN}). */
    private String quoteGenerated(String identifier) {
        if (identifier == null || !GENERATED_IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalStateException("unsafe generated identifier reached the DDL layer: " + identifier);
        }
        return "\"" + identifier + "\"";
    }
}
