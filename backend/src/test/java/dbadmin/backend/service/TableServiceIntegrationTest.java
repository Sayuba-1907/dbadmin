package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.DataColumn;
import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.NotificationRepository;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

// Every test here goes through the real service, which in turn runs real
// CREATE/ALTER/DROP TABLE against the Testcontainers Postgres. Assertions
// check both the metadata (DataTable/DataColumn rows) and the physical database
// (information_schema), so a test only passes if the real table actually
// matches what the metadata claims.
//
// @WithMockUser: TableService artik her mutasyonda AuditLogService uzerinden "kim yapti"
// bilgisini SecurityContext'ten okuyor (bkz. requirement-audit-log.md) — gercek kullanimda
// (controller uzerinden) bu context JWT filtresiyle hep dolu olur, ama bu testler servisi
// dogrudan cagirdigi icin (HTTP katmanini atlayarak) elle doldurulmasi gerekiyor. User adi
// "admin": KullaniciSeeder'in DB bossa olusturdugu ilk user, testin ayrica bir user
// yaratmasina gerek kalmiyor.
@WithMockUser(username = "admin", roles = "ADMIN")
class TableServiceIntegrationTest extends AbstractIntegrationTest {

    /**
     * Testlerin table kurdugu schema. Eskiden tablolar schemaId=null ile "public"e kuruluyordu;
     * "public" artik gizli ve gecerli bir hedef degil (bkz.
     * {@link SchemaService#RESERVED_SCHEMA_NAME}), o yuzden testler kendi schema'sini kullaniyor.
     */
    private static final String TEST_SCHEMA = "test_sema";

    @Autowired
    private TableService tableService;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserService userService;

    /**
     * requirement-websocket-notifications.md testleri icin: aktif kimligi gecici olarak baska
     * bir kullaniciya cevirir, {@code aksiyon}'u calistirir, sonra eski kimlige geri doner —
     * bir tablonun sahibinden farkli bir kullanicinin mutasyon yaptigi senaryoyu kurmak icin.
     */
    private void ensureKullanici(String username, Role rol) {
        if (!userRepository.existsByUsername(username)) {
            userService.createUser(username, "parolam1234", rol);
        }
    }

    private void baskaKullaniciOlarak(String username, Role rol, Runnable aksiyon) {
        ensureKullanici(username, rol);
        Authentication eski = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()))));
        try {
            aksiyon.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(eski);
        }
    }

    /** Testler ayni Postgres'i paylasiyor: schema ilk testte olusur, sonrakiler var olani bulur. */
    private Long testSchemaId;

    @BeforeEach
    void ensureTestSchema() {
        testSchemaId = schemaRepository.findByNameIgnoreCase(TEST_SCHEMA)
                .map(Schema::getId)
                .orElseGet(() -> schemaService.createSchema(TEST_SCHEMA).getId());
    }

    private boolean realTableExists(String tableName) {
        return realTableExists(TEST_SCHEMA, tableName);
    }

    private boolean realTableExists(String schemaName, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class, schemaName, tableName);
        return count != null && count > 0;
    }

    private boolean realColumnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, TEST_SCHEMA, tableName, columnName);
        return count != null && count > 0;
    }

    @Test
    void createTablo_createsMetadataAndRealTable() {
        DataTable table = tableService.createTable("ogrenci1", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null), new ColumnSpec("yas", "numeric", null)));

        assertTrue(realTableExists("ogrenci1"));
        assertTrue(realColumnExists("ogrenci1", "ad"));
        assertTrue(realColumnExists("ogrenci1", "yas"));
        assertEquals(2, table.getColumns().size());
    }

    // requirement-websocket-notifications.md Req-2.1: yeni table, onu olusturan aktif
    // kullaniciya baglaniyor. @WithMockUser(username = "admin") sinif seviyesinde tanimli,
    // KullaniciSeeder de DB bossa ayni user adiyla ilk ADMIN'i olusturuyor, o yuzden
    // burada bulunan id ile aktifKullaniciId()'in dondurdugu id eslesmeli.
    @Test
    void createTablo_setsOwnerToActiveUser() {
        DataTable table = tableService.createTable("sahiplitablo1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        assertEquals(adminId, table.getCreatedByUserId());
    }

    /**
     * Sahip ("admin") her testte ayni user oldugu icin {@code countByAliciKullaniciIdAndOkunduMuFalse}
     * paylasilan (JVM boyunca tek Postgres) sayimda diger testlerin bildirimlerini de sayar —
     * bunun yerine SADECE bu teste ozgu tabloya ait notification sayisini sayiyoruz.
     */
    private long bildirimSayisi(Long recipientUserId, Long tableId) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(
                        recipientUserId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(b -> b.getTableId().equals(tableId))
                .count();
    }

    // requirement-websocket-notifications.md Req-2.2/Req-3.3: sahibi olmayan biri (burada
    // "editor_bildirim1") tabloyu etkileyen bir mutasyon yaparsa sahibe ("admin") bir Notification
    // dusmeli.
    @Test
    void addKolon_byNonOwner_createsNotificationForOwner() {
        DataTable table = tableService.createTable("bildirimli1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        Long ownerId = table.getCreatedByUserId();

        baskaKullaniciOlarak("editor_bildirim1", Role.EDITOR,
                () -> tableService.addColumn(table.getId(), new ColumnSpec("yeni", "text", null)));

        assertEquals(1, bildirimSayisi(ownerId, table.getId()));
    }

    // Req-3.3: sahip kendi tablosunda degisiklik yaparsa kendine notification gitmemeli.
    @Test
    void addKolon_byOwner_createsNoNotification() {
        DataTable table = tableService.createTable("bildirimli2", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        Long ownerId = table.getCreatedByUserId();

        tableService.addColumn(table.getId(), new ColumnSpec("yeni", "text", null));

        assertEquals(0, bildirimSayisi(ownerId, table.getId()));
    }

    @Test
    void deleteTablo_byNonOwner_createsNotificationForOwner() {
        DataTable table = tableService.createTable("bildirimli3", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        Long ownerId = table.getCreatedByUserId();
        Long tableId = table.getId();

        baskaKullaniciOlarak("editor_bildirim2", Role.EDITOR, () -> tableService.deleteTable(tableId));

        assertEquals(1, bildirimSayisi(ownerId, tableId));
    }

    @Test
    void createTablo_duplicateName_isConflict() {
        tableService.createTable("ogrenci2", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ConflictException.class,
                () -> tableService.createTable("ogrenci2", testSchemaId, List.of(new ColumnSpec("baska", "text", null))));
    }

    @Test
    void createTablo_invalidTableName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class,
                () -> tableService.createTable("Buyuk", testSchemaId, List.of(new ColumnSpec("ad", "text", null))));

        assertFalse(realTableExists("Buyuk"));
        assertFalse(realTableExists("buyuk"));
    }

    @Test
    void createTablo_invalidColumnType_isRejected() {
        assertThrows(ValidationException.class,
                () -> tableService.createTable("ogrenci3", testSchemaId, List.of(new ColumnSpec("ad", "varchar", null))));

        assertFalse(realTableExists("ogrenci3"));
    }

    @Test
    void renameTablo_renamesRealTable() {
        DataTable table = tableService.createTable("kurs1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        tableService.renameTable(table.getId(), "kurs1_yeni");

        assertFalse(realTableExists("kurs1"));
        assertTrue(realTableExists("kurs1_yeni"));
    }

    @Test
    void renameTablo_sameName_isNoopAndDoesNotError() {
        DataTable table = tableService.createTable("kurs1b", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        DataTable result = tableService.renameTable(table.getId(), "kurs1b");

        assertEquals("kurs1b", result.getName());
        assertTrue(realTableExists("kurs1b"));
    }

    @Test
    void deleteTablo_dropsRealTableAndCascadesColumns() {
        DataTable table = tableService.createTable("kurs2", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null), new ColumnSpec("kontenjan", "numeric", null)));
        Long kolonId = table.getColumns().get(0).getId();

        tableService.deleteTable(table.getId());

        assertFalse(realTableExists("kurs2"));
        assertTrue(columnRepository.findById(kolonId).isEmpty(), "column metadata should be gone with its table");
    }

    @Test
    void addKolon_addsRealColumn() {
        DataTable table = tableService.createTable("urun1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        tableService.addColumn(table.getId(), new ColumnSpec("fiyat", "numeric", null));

        assertTrue(realColumnExists("urun1", "fiyat"));
    }

    @Test
    void deleteKolon_dropsRealColumnButKeepsTag() {
        Tag tag = tagRepository.save(new Tag("onemli"));
        DataTable table = tableService.createTable("urun2", testSchemaId, List.of(new ColumnSpec("ad", "text", tag.getId())));
        DataColumn column = table.getColumns().get(0);

        tableService.deleteColumn(table.getId(), column.getId());

        assertFalse(realColumnExists("urun2", "ad"));
        assertTrue(tagRepository.findById(tag.getId()).isPresent(), "tag must survive its column being deleted");
    }

    @Test
    void renameKolon_renamesRealColumn() {
        DataTable table = tableService.createTable("urun3", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        DataColumn column = table.getColumns().get(0);

        tableService.renameColumn(table.getId(), column.getId(), "isim");

        assertFalse(realColumnExists("urun3", "ad"));
        assertTrue(realColumnExists("urun3", "isim"));
    }

    @Test
    void renameKolon_sameName_isNoopAndDoesNotError() {
        DataTable table = tableService.createTable("urun3b", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        DataColumn column = table.getColumns().get(0);

        DataColumn result = tableService.renameColumn(table.getId(), column.getId(), "ad");

        assertEquals("ad", result.getName());
        assertTrue(realColumnExists("urun3b", "ad"));
    }

    @Test
    void changeKolonTag_updatesReferenceWithoutTouchingRealTable() {
        Tag tag = tagRepository.save(new Tag("etiket1"));
        DataTable table = tableService.createTable("urun4", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        DataColumn column = table.getColumns().get(0);

        DataColumn updated = tableService.changeColumnTag(table.getId(), column.getId(), tag.getId());

        assertEquals(tag.getId(), updated.getTag().getId());
        assertTrue(realColumnExists("urun4", "ad"));
    }

    @Test
    void getTablo_unknownId_isNotFound() {
        assertThrows(NotFoundException.class, () -> tableService.getTable(-1L));
    }

    /**
     * O tablonun gercek PRIMARY KEY constraint'inin hangi kolonlari, hangi sirayla kapsadigini
     * doner. Sira onemli: composite PK'de {@code PRIMARY KEY (kolona, kolonb)} ile
     * {@code (kolonb, kolona)} farkli seylerdir, o yuzden {@code ordinal_position}'a gore siralanir.
     */
    private List<String> realPrimaryKeyColumns(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT kcu.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu "
                        + "  ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name "
                        + "WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY' "
                        + "ORDER BY kcu.ordinal_position",
                String.class, tableName);
    }

    /** O tablonun gercek PRIMARY KEY constraint'inin adi; PK yoksa null. */
    private String realPrimaryKeyConstraintName(String tableName) {
        List<String> names = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = ? AND constraint_type = 'PRIMARY KEY'",
                String.class, tableName);
        return names.isEmpty() ? null : names.get(0);
    }

    @Test
    void createTablo_primaryKeyFlag_becomesRealPrimaryKey() {
        DataTable table = tableService.createTable("urun5", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null, true), new ColumnSpec("kod", "text", null, false)));

        assertTrue(table.getColumns().get(0).isPrimaryKey());
        assertFalse(table.getColumns().get(1).isPrimaryKey());
        // Isaretlenen column artik gercekten tablonun PRIMARY KEY'i.
        assertEquals(List.of("ad"), realPrimaryKeyColumns("urun5"));
        // Ve tabloda kullanicinin tanimlamadigi otomatik bir "id" kolonu yok.
        assertFalse(realColumnExists("urun5", "id"));
    }

    @Test
    void createTablo_compositePrimaryKeyFlags_createRealCompositePrimaryKey() {
        tableService.createTable("urun7", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true), new ColumnSpec("kolonb", "text", null, true)));

        // Hedeflenen cikti: PRIMARY KEY (kolona, kolonb) — iki ayri PK degil, tek bir bilesik PK.
        assertEquals(List.of("kolona", "kolonb"), realPrimaryKeyColumns("urun7"));
        // Constraint adi da Postgres'in kendi verecegi isimle ayni (DBeaver'da boyle gorunur).
        assertEquals("urun7_pkey", realPrimaryKeyConstraintName("urun7"));

        // Tekrar eden (kolona, kolonb) ciftine izin verilmemeli.
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun7\" (kolona, kolonb) VALUES ('x', 'y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun7\" (kolona, kolonb) VALUES ('x', 'y')"));
        // PK kolonlari Postgres tarafindan otomatik NOT NULL yapilir: bos deger de reddedilmeli.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun7\" (kolona) VALUES ('z')"));
    }

    @Test
    void createTablo_noPrimaryKeyFlag_createsTableWithoutPrimaryKey() {
        tableService.createTable("urun12", testSchemaId, List.of(new ColumnSpec("ad", "text", null, false)));

        // Hicbir column isaretlenmediyse PK'siz table kurulur — eskiden otomatik "id" PK'si vardi.
        assertEquals(List.of(), realPrimaryKeyColumns("urun12"));
        assertFalse(realColumnExists("urun12", "id"));
    }

    @Test
    void addKolon_markingSecondColumnAsPrimaryKey_widensRealPrimaryKey() {
        DataTable table = tableService.createTable("urun8", testSchemaId, List.of(new ColumnSpec("kolona", "text", null, true)));

        tableService.addColumn(table.getId(), new ColumnSpec("kolonb", "text", null, true));

        assertEquals(List.of("kolona", "kolonb"), realPrimaryKeyColumns("urun8"));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun8\" (kolona, kolonb) VALUES ('x', 'y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun8\" (kolona, kolonb) VALUES ('x', 'y')"));
    }

    @Test
    void deleteKolon_removingPrimaryKeyColumn_shrinksPrimaryKeyToRemainingColumns() {
        DataTable table = tableService.createTable("urun9", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true), new ColumnSpec("kolonb", "text", null, true)));
        Long aId = table.getColumns().get(0).getId();

        tableService.deleteColumn(table.getId(), aId);

        // "kolonb" tek basina kalan tek PK kolonu oldugu icin PK artik sadece onu kapsamali.
        assertEquals(List.of("kolonb"), realPrimaryKeyColumns("urun9"));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun9\" (kolonb) VALUES ('y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun9\" (kolonb) VALUES ('y')"));
    }

    @Test
    void renameTablo_keepsPrimaryKeyEnforcedUnderNewName() {
        DataTable table = tableService.createTable("urun11", testSchemaId, List.of(new ColumnSpec("ad", "text", null, true)));

        tableService.renameTable(table.getId(), "urun11_yeni");

        // Constraint de tabloyla birlikte yeni ismi almis olmali.
        assertEquals("urun11_yeni_pkey", realPrimaryKeyConstraintName("urun11_yeni"));

        // Rename sonrasi column ekleyip PK'nin genisletilebildigini de dogrula (eski isimle
        // takilip kalmadigini gosterir).
        tableService.addColumn(table.getId(), new ColumnSpec("kod", "text", null, true));
        assertEquals(List.of("ad", "kod"), realPrimaryKeyColumns("urun11_yeni"));

        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun11_yeni\" (ad, kod) VALUES ('x', 'k')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO \"" + TEST_SCHEMA + "\".\"urun11_yeni\" (ad, kod) VALUES ('x', 'k')"));
    }

    @Test
    void deleteKolon_removingOnlyPrimaryKeyColumn_dropsPrimaryKeyEntirely() {
        DataTable table = tableService.createTable("urun10", testSchemaId,
                List.of(new ColumnSpec("ad", "text", null, true), new ColumnSpec("kod", "text", null, false)));
        Long adId = table.getColumns().get(0).getId();

        tableService.deleteColumn(table.getId(), adId);

        assertEquals(List.of(), realPrimaryKeyColumns("urun10"));
    }

    @Test
    void changeKolonPrimaryKey_markingExistingColumn_makesItPartOfRealPrimaryKey() {
        // Bu ucun varlik sebebi: column olusturulurken isaretlenmemis, sonradan PK yapilmak istenen
        // column. Eskiden tek care kolonu silip yeniden eklemekti (yani veriyi kaybetmekti).
        DataTable table = tableService.createTable("urun13", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true), new ColumnSpec("kolonb", "text", null, false)));
        Long bId = table.getColumns().get(1).getId();
        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun13"));

        DataColumn updated = tableService.changeColumnPrimaryKey(table.getId(), bId, true);

        assertTrue(updated.isPrimaryKey());
        assertEquals(List.of("kolona", "kolonb"), realPrimaryKeyColumns("urun13"));
    }

    @Test
    void changeKolonPrimaryKey_unmarkingColumn_shrinksRealPrimaryKey() {
        DataTable table = tableService.createTable("urun14", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true), new ColumnSpec("kolonb", "text", null, true)));
        Long bId = table.getColumns().get(1).getId();

        tableService.changeColumnPrimaryKey(table.getId(), bId, false);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun14"));
    }

    @Test
    void changeKolonPrimaryKey_unmarkingLastPrimaryKeyColumn_leavesTableWithoutPrimaryKey() {
        DataTable table = tableService.createTable("urun15", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true)));
        Long aId = table.getColumns().get(0).getId();

        tableService.changeColumnPrimaryKey(table.getId(), aId, false);

        assertEquals(List.of(), realPrimaryKeyColumns("urun15"));
        // DataColumn duruyor, sadece PK'nin parcasi degil.
        assertTrue(realColumnExists("urun15", "kolona"));
    }

    @Test
    void changeKolonPrimaryKey_onPkLessTable_givesItAPrimaryKey() {
        // "ogr" gibi hicbir kolonu isaretlenmemis eski tablolarin senaryosu.
        DataTable table = tableService.createTable("urun16", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, false), new ColumnSpec("kolonb", "text", null, false)));
        assertEquals(List.of(), realPrimaryKeyColumns("urun16"));
        Long aId = table.getColumns().get(0).getId();

        tableService.changeColumnPrimaryKey(table.getId(), aId, true);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun16"));
    }

    @Test
    void changeKolonPrimaryKey_sameValue_isNoOp() {
        DataTable table = tableService.createTable("urun17", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true)));
        Long aId = table.getColumns().get(0).getId();

        tableService.changeColumnPrimaryKey(table.getId(), aId, true);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun17"));
    }

    @Test
    void changeKolonPrimaryKey_unknownKolon_isNotFound() {
        DataTable table = tableService.createTable("urun18", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, false)));

        assertThrows(NotFoundException.class,
                () -> tableService.changeColumnPrimaryKey(table.getId(), -1L, true));
    }

    @Test
    void changeKolonPrimaryKey_columnWithNullValues_isRejectedAndLeavesMetadataUnchanged() {
        // PRIMARY KEY kolonlari Postgres tarafindan otomatik NOT NULL yapilir; mevcut satirlarda
        // NULL varsa DDL patlar. Onemli olan: metadata'daki isaretin de geri alinmasi.
        DataTable table = tableService.createTable("urun19", testSchemaId,
                List.of(new ColumnSpec("kolona", "text", null, true), new ColumnSpec("kolonb", "text", null, false)));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun19\" (kolona) VALUES ('x')");
        Long bId = table.getColumns().get(1).getId();

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> tableService.changeColumnPrimaryKey(table.getId(), bId, true));

        // Gercek PK degismedi ve metadata da eski halinde (transaction geri alindi).
        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun19"));
        assertFalse(tableService.getTable(table.getId()).getColumns().get(1).isPrimaryKey());
    }

    @Test
    void createTablo_noSchemaId_isRejected() {
        // Eskiden bu durumda table sessizce "public"e kuruluyordu. "public" gizlendigi icin boyle
        // bir table olusturuldugu anda arayuzde gorunmez olurdu — o yuzden artik hata veriyoruz.
        assertThrows(ValidationException.class,
                () -> tableService.createTable("urun6", null, List.of(new ColumnSpec("ad", "text", null))));

        assertFalse(realTableExists("public", "urun6"));
    }

    @Test
    void createTablo_intoPublicSchema_isNotFound() {
        Schema legacyPublic = schemaRepository.save(new Schema(SchemaService.RESERVED_SCHEMA_NAME));
        try {
            assertThrows(NotFoundException.class, () -> tableService.createTable(
                    "urun6b", legacyPublic.getId(), List.of(new ColumnSpec("ad", "text", null))));

            assertFalse(realTableExists("public", "urun6b"));
        } finally {
            schemaRepository.delete(legacyPublic);
        }
    }

    @Test
    void createTablo_withSchemaId_createsRealTableInThatSchema() {
        Schema schema = schemaService.createSchema("raporlama1");

        DataTable table = tableService.createTable("rapor1", schema.getId(),
                List.of(new ColumnSpec("ad", "text", null)));

        assertEquals(schema.getId(), table.getSchema().getId());
        assertTrue(realTableExists("raporlama1", "rapor1"));
        assertFalse(realTableExists(TEST_SCHEMA, "rapor1"));
    }

    @Test
    void createTablo_unknownSchemaId_isNotFound() {
        assertThrows(NotFoundException.class,
                () -> tableService.createTable("rapor2", -1L, List.of(new ColumnSpec("ad", "text", null))));

        assertFalse(realTableExists("rapor2"));
    }

    @Test
    void listTablolarBySchema_returnsOnlyThatSchemasTables() {
        Schema schema = schemaService.createSchema("raporlama2");
        DataTable inSchema = tableService.createTable("rapor3", schema.getId(),
                List.of(new ColumnSpec("ad", "text", null)));
        tableService.createTable("rapor4", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        List<DataTable> result = tableService.listTablesBySchema(schema.getId());

        assertEquals(1, result.size());
        assertEquals(inSchema.getId(), result.get(0).getId());
    }

    @Test
    void changeSchema_movesRealTableToNewSchema() {
        Schema hedefSchema = schemaService.createSchema("hedef1");
        DataTable table = tableService.createTable("tasinan1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        DataTable moved = tableService.changeSchema(table.getId(), hedefSchema.getId());

        assertEquals(hedefSchema.getId(), moved.getSchema().getId());
        assertTrue(realTableExists("hedef1", "tasinan1"));
        assertFalse(realTableExists(TEST_SCHEMA, "tasinan1"));
    }

    @Test
    void changeSchema_missingSchemaId_isRejected() {
        DataTable table = tableService.createTable("tasinan2", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tableService.changeSchema(table.getId(), null));

        assertTrue(realTableExists(TEST_SCHEMA, "tasinan2"));
    }

    @Test
    void changeSchema_unknownSchemaId_isNotFound() {
        DataTable table = tableService.createTable("tasinan3", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(NotFoundException.class, () -> tableService.changeSchema(table.getId(), -1L));

        assertTrue(realTableExists(TEST_SCHEMA, "tasinan3"));
    }

    @Test
    void changeSchema_sameSchema_isNoopAndDoesNotError() {
        DataTable table = tableService.createTable("tasinan4", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        Long ayniSchemaId = table.getSchema().getId();

        DataTable result = tableService.changeSchema(table.getId(), ayniSchemaId);

        assertEquals(ayniSchemaId, result.getSchema().getId());
        assertTrue(realTableExists(TEST_SCHEMA, "tasinan4"));
    }

    // ---------------------------------------------------------------------
    // applyChanges — "Kaydet'e basinca hepsi birden gitsin" akisi.

    // Gercek arayuzdeki "Kaydet" butonu hep applyChanges'i kullanir — tekil addColumn/deleteColumn/...
    // uclarina eklenen bildir() cagrilari pratikte tetiklenmiyordu. Bu test o bosluk icin: sahibi
    // olmayan biri "Kaydet"e basarsa (toplu degisiklik) tek bir ozet notification olusmali.
    @Test
    void applyChanges_byNonOwner_createsNotificationForOwner() {
        DataTable table = tableService.createTable("bildirimli_toplu1", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));
        Long ownerId = table.getCreatedByUserId();
        Long tableId = table.getId();

        baskaKullaniciOlarak("editor_bildirim3", Role.EDITOR, () -> tableService.applyChanges(
                tableId, "bildirimli_toplu1_yeni", null, List.of(),
                List.of(new ColumnSpec("yeni_kolon", "text", null)), List.of()));

        assertEquals(1, bildirimSayisi(ownerId, tableId));
    }

    @Test
    void applyChanges_renameAddDeleteUpdateAndMoveSchema_allApplyTogether() {
        DataTable table = tableService.createTable("toplu1", testSchemaId, List.of(
                new ColumnSpec("silinecek", "text", null, false),
                new ColumnSpec("guncellenecek", "text", null, false)));
        Long silinecekId = table.getColumns().get(0).getId();
        Long guncellenecekId = table.getColumns().get(1).getId();
        Tag tag = tagRepository.save(new Tag("toplu_tag"));
        Schema ikinciSchema = schemaService.createSchema("toplu_hedef_sema");

        DataTable sonuc = tableService.applyChanges(table.getId(), "toplu1_yeni", ikinciSchema.getId(),
                List.of(silinecekId),
                List.of(new ColumnSpec("yeni_kolon", "numeric", null, true)),
                List.of(new ColumnUpdate(guncellenecekId, "guncellendi", tag.getId(), true)));

        assertEquals("toplu1_yeni", sonuc.getName());
        assertEquals(ikinciSchema.getId(), sonuc.getSchema().getId());
        assertTrue(realTableExists("toplu_hedef_sema", "toplu1_yeni"));
        assertFalse(realColumnExists("toplu1_yeni", "silinecek"));
        List<String> kolonAdlari = sonuc.getColumns().stream().map(DataColumn::getName).toList();
        assertTrue(kolonAdlari.contains("guncellendi"));
        assertTrue(kolonAdlari.contains("yeni_kolon"));
        DataColumn guncellenenKolon = sonuc.getColumns().stream()
                .filter(k -> k.getId().equals(guncellenecekId)).findFirst().orElseThrow();
        assertEquals(tag.getId(), guncellenenKolon.getTag().getId());
        assertTrue(guncellenenKolon.isPrimaryKey());
    }

    @Test
    void applyChanges_newColumnsWithDuplicateNameInSameRequest_isRejectedByExistingAddKolonCheck() {
        // Ayri bir "batch icinde tekrar eden isim" kontrolu yazmadik: addColumn zaten her yeni
        // kolonu tek tek kaydettigi icin, listedeki ikinci "ayniisim" kolonu addColumn'un kendi
        // existsByTabloAndName kontrolune (birincisi az once kaydedildigi icin) takilir.
        DataTable table = tableService.createTable("toplu2", testSchemaId, List.of(new ColumnSpec("ad", "text", null)));

        assertThrows(ConflictException.class, () -> tableService.applyChanges(table.getId(), null, null,
                List.of(),
                List.of(new ColumnSpec("ayniisim", "text", null), new ColumnSpec("ayniisim", "text", null)),
                List.of()));
    }

    @Test
    void applyChanges_unknownTablo_isNotFound() {
        assertThrows(NotFoundException.class,
                () -> tableService.applyChanges(-1L, "yeni_isim", null, List.of(), List.of(), List.of()));
    }

    /**
     * En kritik test: N alt-islemden biri (burada bir kolonu PK yapmak, NULL deger yuzunden)
     * patlarsa, o ana kadar uygulanmis GORUNEN hicbir sey (rename, yeni column, silinen column)
     * kalici olmamali — hepsi ayni transaction'da, hepsi ya da hicbiri.
     */
    @Test
    void applyChanges_whenOneStepFails_rollsBackEveryOtherStepToo() {
        DataTable table = tableService.createTable("toplu3", testSchemaId, List.of(
                new ColumnSpec("nullolan", "text", null, false),
                new ColumnSpec("silinecek", "text", null, false)));
        Long nullolanId = table.getColumns().get(0).getId();
        Long silinecekId = table.getColumns().get(1).getId();
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"toplu3\" (nullolan) VALUES (NULL)");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> tableService.applyChanges(table.getId(), "toplu3_yeni_isim", null,
                        List.of(silinecekId),
                        List.of(new ColumnSpec("yeni_kolon", "text", null)),
                        List.of(new ColumnUpdate(nullolanId, "nullolan", null, true))));

        // Rename, silme ve ekleme GORUNURDE onceki adimlarda basarili olmus olabilir ama hicbiri
        // kalici degil: hem metadata hem gercek table eski haliyle duruyor.
        assertFalse(realTableExists(TEST_SCHEMA, "toplu3_yeni_isim"));
        assertTrue(realTableExists(TEST_SCHEMA, "toplu3"));
        assertTrue(realColumnExists("toplu3", "silinecek"));
        assertFalse(realColumnExists("toplu3", "yeni_kolon"));
        DataTable guncelHali = tableService.getTable(table.getId());
        assertEquals("toplu3", guncelHali.getName());
        assertEquals(2, guncelHali.getColumns().size());
        assertEquals(List.of(), realPrimaryKeyColumns("toplu3"));
    }
}
