package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.entity.Tag;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.KolonRepository;
import dbadmin.backend.repository.TagRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Every test here goes through the real service, which in turn runs real
// CREATE/ALTER/DROP TABLE against the Testcontainers Postgres. Assertions
// check both the metadata (Tablo/Kolon rows) and the physical database
// (information_schema), so a test only passes if the real table actually
// matches what the metadata claims.
class TabloServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TabloService tabloService;

    @Autowired
    private KolonRepository kolonRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean realTableExists(String tableName) {
        return realTableExists("public", tableName);
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
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    @Test
    void createTablo_createsMetadataAndRealTable() {
        Tablo tablo = tabloService.createTablo("ogrenci1", null,
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("yas", "numeric", null)));

        assertTrue(realTableExists("ogrenci1"));
        assertTrue(realColumnExists("ogrenci1", "ad"));
        assertTrue(realColumnExists("ogrenci1", "yas"));
        assertEquals(2, tablo.getKolonlar().size());
    }

    @Test
    void createTablo_duplicateName_isConflict() {
        tabloService.createTablo("ogrenci2", null, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ConflictException.class,
                () -> tabloService.createTablo("ogrenci2", null, List.of(new KolonTanimi("baska", "text", null))));
    }

    @Test
    void createTablo_invalidTableName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("Buyuk", null, List.of(new KolonTanimi("ad", "text", null))));

        assertFalse(realTableExists("Buyuk"));
        assertFalse(realTableExists("buyuk"));
    }

    @Test
    void createTablo_invalidColumnType_isRejected() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("ogrenci3", null, List.of(new KolonTanimi("ad", "varchar", null))));

        assertFalse(realTableExists("ogrenci3"));
    }

    @Test
    void renameTablo_renamesRealTable() {
        Tablo tablo = tabloService.createTablo("kurs1", null, List.of(new KolonTanimi("ad", "text", null)));

        tabloService.renameTablo(tablo.getId(), "kurs1_yeni");

        assertFalse(realTableExists("kurs1"));
        assertTrue(realTableExists("kurs1_yeni"));
    }

    @Test
    void deleteTablo_dropsRealTableAndCascadesColumns() {
        Tablo tablo = tabloService.createTablo("kurs2", null,
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("kontenjan", "numeric", null)));
        Long kolonId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteTablo(tablo.getId());

        assertFalse(realTableExists("kurs2"));
        assertTrue(kolonRepository.findById(kolonId).isEmpty(), "kolon metadata should be gone with its table");
    }

    @Test
    void addKolon_addsRealColumn() {
        Tablo tablo = tabloService.createTablo("urun1", null, List.of(new KolonTanimi("ad", "text", null)));

        tabloService.addKolon(tablo.getId(), new KolonTanimi("fiyat", "numeric", null));

        assertTrue(realColumnExists("urun1", "fiyat"));
    }

    @Test
    void deleteKolon_dropsRealColumnButKeepsTag() {
        Tag tag = tagRepository.save(new Tag("onemli"));
        Tablo tablo = tabloService.createTablo("urun2", null, List.of(new KolonTanimi("ad", "text", tag.getId())));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.deleteKolon(tablo.getId(), kolon.getId());

        assertFalse(realColumnExists("urun2", "ad"));
        assertTrue(tagRepository.findById(tag.getId()).isPresent(), "tag must survive its column being deleted");
    }

    @Test
    void renameKolon_renamesRealColumn() {
        Tablo tablo = tabloService.createTablo("urun3", null, List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.renameKolon(tablo.getId(), kolon.getId(), "isim");

        assertFalse(realColumnExists("urun3", "ad"));
        assertTrue(realColumnExists("urun3", "isim"));
    }

    @Test
    void changeKolonTag_updatesReferenceWithoutTouchingRealTable() {
        Tag tag = tagRepository.save(new Tag("etiket1"));
        Tablo tablo = tabloService.createTablo("urun4", null, List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        Kolon updated = tabloService.changeKolonTag(tablo.getId(), kolon.getId(), tag.getId());

        assertEquals(tag.getId(), updated.getTag().getId());
        assertTrue(realColumnExists("urun4", "ad"));
    }

    @Test
    void getTablo_unknownId_isNotFound() {
        assertThrows(NotFoundException.class, () -> tabloService.getTablo(-1L));
    }

    /** Bir tablonun tek bir kolonu icin real UNIQUE constraint'in olup olmadigini doner. */
    private boolean realUniqueConstraintExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu "
                        + "  ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name "
                        + "WHERE tc.table_name = ? AND tc.constraint_type = 'UNIQUE' AND kcu.column_name = ?",
                Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    /** O tablonun gercek PRIMARY KEY constraint'inin hangi kolonlari kapsadigini doner. */
    private List<String> realPrimaryKeyColumns(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT kcu.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu "
                        + "  ON tc.constraint_name = kcu.constraint_name AND tc.table_name = kcu.table_name "
                        + "WHERE tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY'",
                String.class, tableName);
    }

    @Test
    void createTablo_primaryKeyFlag_doesNotChangeRealPkButAddsRealUniqueConstraint() {
        Tablo tablo = tabloService.createTablo("urun5", null,
                List.of(new KolonTanimi("ad", "text", null, true), new KolonTanimi("kod", "text", null, false)));

        assertTrue(tablo.getKolonlar().get(0).isPrimaryKey());
        assertFalse(tablo.getKolonlar().get(1).isPrimaryKey());
        // Gercek PRIMARY KEY hala sadece otomatik "id" kolonunda.
        assertEquals(List.of("id"), realPrimaryKeyColumns("urun5"));
        // Ama flag artik kozmetik degil: "ad" uzerinde gercek bir UNIQUE constraint var, "kod" uzerinde yok.
        assertTrue(realUniqueConstraintExists("urun5", "ad"));
        assertFalse(realUniqueConstraintExists("urun5", "kod"));
    }

    @Test
    void createTablo_compositePrimaryKeyFlags_createRealCompositeUniqueConstraint() {
        tabloService.createTablo("urun7", null,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, true)));

        assertTrue(realUniqueConstraintExists("urun7", "kolona"));
        assertTrue(realUniqueConstraintExists("urun7", "kolonb"));
        // Tekrar eden (kolona, kolonb) ciftine izin verilmemeli - composite unique constraint devrede.
        jdbcTemplate.update("INSERT INTO \"urun7\" (kolona, kolonb) VALUES ('x', 'y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"urun7\" (kolona, kolonb) VALUES ('x', 'y')"));
    }

    @Test
    void addKolon_markingSecondColumnAsPrimaryKey_widensRealUniqueConstraint() {
        Tablo tablo = tabloService.createTablo("urun8", null, List.of(new KolonTanimi("kolona", "text", null, true)));

        tabloService.addKolon(tablo.getId(), new KolonTanimi("kolonb", "text", null, true));

        assertTrue(realUniqueConstraintExists("urun8", "kolona"));
        assertTrue(realUniqueConstraintExists("urun8", "kolonb"));
        jdbcTemplate.update("INSERT INTO \"urun8\" (kolona, kolonb) VALUES ('x', 'y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"urun8\" (kolona, kolonb) VALUES ('x', 'y')"));
    }

    @Test
    void deleteKolon_removingPrimaryKeyColumn_shrinksConstraintToRemainingColumns() {
        Tablo tablo = tabloService.createTablo("urun9", null,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, true)));
        Long aId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteKolon(tablo.getId(), aId);

        // "kolonb" tek basina artik PK-isaretli tek kolon oldugu icin kendi basina unique olmali.
        assertTrue(realUniqueConstraintExists("urun9", "kolonb"));
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT tc.constraint_name) FROM information_schema.table_constraints tc "
                        + "WHERE tc.table_name = ? AND tc.constraint_type = 'UNIQUE'",
                Integer.class, "urun9");
        assertEquals(1, uniqueConstraintCount);
        jdbcTemplate.update("INSERT INTO \"urun9\" (kolonb) VALUES ('y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"urun9\" (kolonb) VALUES ('y')"));
    }

    @Test
    void renameTablo_keepsPrimaryKeyUniqueConstraintEnforcedUnderNewName() {
        Tablo tablo = tabloService.createTablo("urun11", null, List.of(new KolonTanimi("ad", "text", null, true)));

        tabloService.renameTablo(tablo.getId(), "urun11_yeni");

        assertTrue(realUniqueConstraintExists("urun11_yeni", "ad"));
        jdbcTemplate.update("INSERT INTO \"urun11_yeni\" (ad) VALUES ('x')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"urun11_yeni\" (ad) VALUES ('x')"));

        // Rename sonrasi kolon ekleyip constraint'in genisletilebildigini de dogrula (eski isimle
        // takilip kalmadigini gosterir).
        tabloService.addKolon(tablo.getId(), new KolonTanimi("kod", "text", null, true));
        assertTrue(realUniqueConstraintExists("urun11_yeni", "kod"));
    }

    @Test
    void deleteKolon_removingOnlyPrimaryKeyColumn_dropsConstraintEntirely() {
        Tablo tablo = tabloService.createTablo("urun10", null, List.of(new KolonTanimi("ad", "text", null, true)));
        Long adId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteKolon(tablo.getId(), adId);

        Integer uniqueConstraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_name = ? AND constraint_type = 'UNIQUE'",
                Integer.class, "urun10");
        assertEquals(0, uniqueConstraintCount);
    }

    @Test
    void createTablo_noSchemaId_defaultsToPublicSchema() {
        Tablo tablo = tabloService.createTablo("urun6", null, List.of(new KolonTanimi("ad", "text", null)));

        assertEquals("public", tablo.getSchema().getName());
        assertTrue(realTableExists("public", "urun6"));
    }

    @Test
    void createTablo_withSchemaId_createsRealTableInThatSchema() {
        Schema schema = schemaService.createSchema("raporlama1");

        Tablo tablo = tabloService.createTablo("rapor1", schema.getId(),
                List.of(new KolonTanimi("ad", "text", null)));

        assertEquals(schema.getId(), tablo.getSchema().getId());
        assertTrue(realTableExists("raporlama1", "rapor1"));
        assertFalse(realTableExists("public", "rapor1"));
    }

    @Test
    void createTablo_unknownSchemaId_isNotFound() {
        assertThrows(NotFoundException.class,
                () -> tabloService.createTablo("rapor2", -1L, List.of(new KolonTanimi("ad", "text", null))));

        assertFalse(realTableExists("rapor2"));
    }

    @Test
    void listTablolarBySchema_returnsOnlyThatSchemasTables() {
        Schema schema = schemaService.createSchema("raporlama2");
        Tablo inSchema = tabloService.createTablo("rapor3", schema.getId(),
                List.of(new KolonTanimi("ad", "text", null)));
        tabloService.createTablo("rapor4", null, List.of(new KolonTanimi("ad", "text", null)));

        List<Tablo> result = tabloService.listTablolarBySchema(schema.getId());

        assertEquals(1, result.size());
        assertEquals(inSchema.getId(), result.get(0).getId());
    }

    @Test
    void changeSchema_movesRealTableToNewSchema() {
        Schema hedefSchema = schemaService.createSchema("hedef1");
        Tablo tablo = tabloService.createTablo("tasinan1", null, List.of(new KolonTanimi("ad", "text", null)));

        Tablo moved = tabloService.changeSchema(tablo.getId(), hedefSchema.getId());

        assertEquals(hedefSchema.getId(), moved.getSchema().getId());
        assertTrue(realTableExists("hedef1", "tasinan1"));
        assertFalse(realTableExists("public", "tasinan1"));
    }

    @Test
    void changeSchema_missingSchemaId_isRejected() {
        Tablo tablo = tabloService.createTablo("tasinan2", null, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tabloService.changeSchema(tablo.getId(), null));

        assertTrue(realTableExists("public", "tasinan2"));
    }

    @Test
    void changeSchema_unknownSchemaId_isNotFound() {
        Tablo tablo = tabloService.createTablo("tasinan3", null, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(NotFoundException.class, () -> tabloService.changeSchema(tablo.getId(), -1L));

        assertTrue(realTableExists("public", "tasinan3"));
    }

    @Test
    void changeSchema_sameSchema_isNoopAndDoesNotError() {
        Tablo tablo = tabloService.createTablo("tasinan4", null, List.of(new KolonTanimi("ad", "text", null)));
        Long publicSchemaId = tablo.getSchema().getId();

        Tablo result = tabloService.changeSchema(tablo.getId(), publicSchemaId);

        assertEquals(publicSchemaId, result.getSchema().getId());
        assertTrue(realTableExists("public", "tasinan4"));
    }
}
