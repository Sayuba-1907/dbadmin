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
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// Every test here goes through the real service, which in turn runs real
// CREATE/ALTER/DROP TABLE against the Testcontainers Postgres. Assertions
// check both the metadata (Tablo/Kolon rows) and the physical database
// (information_schema), so a test only passes if the real table actually
// matches what the metadata claims.
class TabloServiceIntegrationTest extends AbstractIntegrationTest {

    /**
     * Testlerin tablo kurdugu schema. Eskiden tablolar schemaId=null ile "public"e kuruluyordu;
     * "public" artik gizli ve gecerli bir hedef degil (bkz.
     * {@link SchemaService#RESERVED_SCHEMA_NAME}), o yuzden testler kendi schema'sini kullaniyor.
     */
    private static final String TEST_SCHEMA = "test_sema";

    @Autowired
    private TabloService tabloService;

    @Autowired
    private KolonRepository kolonRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        Tablo tablo = tabloService.createTablo("ogrenci1", testSchemaId,
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("yas", "numeric", null)));

        assertTrue(realTableExists("ogrenci1"));
        assertTrue(realColumnExists("ogrenci1", "ad"));
        assertTrue(realColumnExists("ogrenci1", "yas"));
        assertEquals(2, tablo.getKolonlar().size());
    }

    @Test
    void createTablo_duplicateName_isConflict() {
        tabloService.createTablo("ogrenci2", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ConflictException.class,
                () -> tabloService.createTablo("ogrenci2", testSchemaId, List.of(new KolonTanimi("baska", "text", null))));
    }

    @Test
    void createTablo_invalidTableName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("Buyuk", testSchemaId, List.of(new KolonTanimi("ad", "text", null))));

        assertFalse(realTableExists("Buyuk"));
        assertFalse(realTableExists("buyuk"));
    }

    @Test
    void createTablo_invalidColumnType_isRejected() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("ogrenci3", testSchemaId, List.of(new KolonTanimi("ad", "varchar", null))));

        assertFalse(realTableExists("ogrenci3"));
    }

    @Test
    void renameTablo_renamesRealTable() {
        Tablo tablo = tabloService.createTablo("kurs1", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        tabloService.renameTablo(tablo.getId(), "kurs1_yeni");

        assertFalse(realTableExists("kurs1"));
        assertTrue(realTableExists("kurs1_yeni"));
    }

    @Test
    void renameTablo_sameName_isNoopAndDoesNotError() {
        Tablo tablo = tabloService.createTablo("kurs1b", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        Tablo result = tabloService.renameTablo(tablo.getId(), "kurs1b");

        assertEquals("kurs1b", result.getName());
        assertTrue(realTableExists("kurs1b"));
    }

    @Test
    void deleteTablo_dropsRealTableAndCascadesColumns() {
        Tablo tablo = tabloService.createTablo("kurs2", testSchemaId,
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("kontenjan", "numeric", null)));
        Long kolonId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteTablo(tablo.getId());

        assertFalse(realTableExists("kurs2"));
        assertTrue(kolonRepository.findById(kolonId).isEmpty(), "kolon metadata should be gone with its table");
    }

    @Test
    void addKolon_addsRealColumn() {
        Tablo tablo = tabloService.createTablo("urun1", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        tabloService.addKolon(tablo.getId(), new KolonTanimi("fiyat", "numeric", null));

        assertTrue(realColumnExists("urun1", "fiyat"));
    }

    @Test
    void deleteKolon_dropsRealColumnButKeepsTag() {
        Tag tag = tagRepository.save(new Tag("onemli"));
        Tablo tablo = tabloService.createTablo("urun2", testSchemaId, List.of(new KolonTanimi("ad", "text", tag.getId())));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.deleteKolon(tablo.getId(), kolon.getId());

        assertFalse(realColumnExists("urun2", "ad"));
        assertTrue(tagRepository.findById(tag.getId()).isPresent(), "tag must survive its column being deleted");
    }

    @Test
    void renameKolon_renamesRealColumn() {
        Tablo tablo = tabloService.createTablo("urun3", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.renameKolon(tablo.getId(), kolon.getId(), "isim");

        assertFalse(realColumnExists("urun3", "ad"));
        assertTrue(realColumnExists("urun3", "isim"));
    }

    @Test
    void renameKolon_sameName_isNoopAndDoesNotError() {
        Tablo tablo = tabloService.createTablo("urun3b", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        Kolon result = tabloService.renameKolon(tablo.getId(), kolon.getId(), "ad");

        assertEquals("ad", result.getName());
        assertTrue(realColumnExists("urun3b", "ad"));
    }

    @Test
    void changeKolonTag_updatesReferenceWithoutTouchingRealTable() {
        Tag tag = tagRepository.save(new Tag("etiket1"));
        Tablo tablo = tabloService.createTablo("urun4", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        Kolon updated = tabloService.changeKolonTag(tablo.getId(), kolon.getId(), tag.getId());

        assertEquals(tag.getId(), updated.getTag().getId());
        assertTrue(realColumnExists("urun4", "ad"));
    }

    @Test
    void getTablo_unknownId_isNotFound() {
        assertThrows(NotFoundException.class, () -> tabloService.getTablo(-1L));
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
        Tablo tablo = tabloService.createTablo("urun5", testSchemaId,
                List.of(new KolonTanimi("ad", "text", null, true), new KolonTanimi("kod", "text", null, false)));

        assertTrue(tablo.getKolonlar().get(0).isPrimaryKey());
        assertFalse(tablo.getKolonlar().get(1).isPrimaryKey());
        // Isaretlenen kolon artik gercekten tablonun PRIMARY KEY'i.
        assertEquals(List.of("ad"), realPrimaryKeyColumns("urun5"));
        // Ve tabloda kullanicinin tanimlamadigi otomatik bir "id" kolonu yok.
        assertFalse(realColumnExists("urun5", "id"));
    }

    @Test
    void createTablo_compositePrimaryKeyFlags_createRealCompositePrimaryKey() {
        tabloService.createTablo("urun7", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, true)));

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
        tabloService.createTablo("urun12", testSchemaId, List.of(new KolonTanimi("ad", "text", null, false)));

        // Hicbir kolon isaretlenmediyse PK'siz tablo kurulur — eskiden otomatik "id" PK'si vardi.
        assertEquals(List.of(), realPrimaryKeyColumns("urun12"));
        assertFalse(realColumnExists("urun12", "id"));
    }

    @Test
    void addKolon_markingSecondColumnAsPrimaryKey_widensRealPrimaryKey() {
        Tablo tablo = tabloService.createTablo("urun8", testSchemaId, List.of(new KolonTanimi("kolona", "text", null, true)));

        tabloService.addKolon(tablo.getId(), new KolonTanimi("kolonb", "text", null, true));

        assertEquals(List.of("kolona", "kolonb"), realPrimaryKeyColumns("urun8"));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun8\" (kolona, kolonb) VALUES ('x', 'y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun8\" (kolona, kolonb) VALUES ('x', 'y')"));
    }

    @Test
    void deleteKolon_removingPrimaryKeyColumn_shrinksPrimaryKeyToRemainingColumns() {
        Tablo tablo = tabloService.createTablo("urun9", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, true)));
        Long aId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteKolon(tablo.getId(), aId);

        // "kolonb" tek basina kalan tek PK kolonu oldugu icin PK artik sadece onu kapsamali.
        assertEquals(List.of("kolonb"), realPrimaryKeyColumns("urun9"));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun9\" (kolonb) VALUES ('y')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun9\" (kolonb) VALUES ('y')"));
    }

    @Test
    void renameTablo_keepsPrimaryKeyEnforcedUnderNewName() {
        Tablo tablo = tabloService.createTablo("urun11", testSchemaId, List.of(new KolonTanimi("ad", "text", null, true)));

        tabloService.renameTablo(tablo.getId(), "urun11_yeni");

        // Constraint de tabloyla birlikte yeni ismi almis olmali.
        assertEquals("urun11_yeni_pkey", realPrimaryKeyConstraintName("urun11_yeni"));

        // Rename sonrasi kolon ekleyip PK'nin genisletilebildigini de dogrula (eski isimle
        // takilip kalmadigini gosterir).
        tabloService.addKolon(tablo.getId(), new KolonTanimi("kod", "text", null, true));
        assertEquals(List.of("ad", "kod"), realPrimaryKeyColumns("urun11_yeni"));

        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun11_yeni\" (ad, kod) VALUES ('x', 'k')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO \"" + TEST_SCHEMA + "\".\"urun11_yeni\" (ad, kod) VALUES ('x', 'k')"));
    }

    @Test
    void deleteKolon_removingOnlyPrimaryKeyColumn_dropsPrimaryKeyEntirely() {
        Tablo tablo = tabloService.createTablo("urun10", testSchemaId,
                List.of(new KolonTanimi("ad", "text", null, true), new KolonTanimi("kod", "text", null, false)));
        Long adId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteKolon(tablo.getId(), adId);

        assertEquals(List.of(), realPrimaryKeyColumns("urun10"));
    }

    @Test
    void changeKolonPrimaryKey_markingExistingColumn_makesItPartOfRealPrimaryKey() {
        // Bu ucun varlik sebebi: kolon olusturulurken isaretlenmemis, sonradan PK yapilmak istenen
        // kolon. Eskiden tek care kolonu silip yeniden eklemekti (yani veriyi kaybetmekti).
        Tablo tablo = tabloService.createTablo("urun13", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, false)));
        Long bId = tablo.getKolonlar().get(1).getId();
        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun13"));

        Kolon updated = tabloService.changeKolonPrimaryKey(tablo.getId(), bId, true);

        assertTrue(updated.isPrimaryKey());
        assertEquals(List.of("kolona", "kolonb"), realPrimaryKeyColumns("urun13"));
    }

    @Test
    void changeKolonPrimaryKey_unmarkingColumn_shrinksRealPrimaryKey() {
        Tablo tablo = tabloService.createTablo("urun14", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, true)));
        Long bId = tablo.getKolonlar().get(1).getId();

        tabloService.changeKolonPrimaryKey(tablo.getId(), bId, false);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun14"));
    }

    @Test
    void changeKolonPrimaryKey_unmarkingLastPrimaryKeyColumn_leavesTableWithoutPrimaryKey() {
        Tablo tablo = tabloService.createTablo("urun15", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true)));
        Long aId = tablo.getKolonlar().get(0).getId();

        tabloService.changeKolonPrimaryKey(tablo.getId(), aId, false);

        assertEquals(List.of(), realPrimaryKeyColumns("urun15"));
        // Kolon duruyor, sadece PK'nin parcasi degil.
        assertTrue(realColumnExists("urun15", "kolona"));
    }

    @Test
    void changeKolonPrimaryKey_onPkLessTable_givesItAPrimaryKey() {
        // "ogr" gibi hicbir kolonu isaretlenmemis eski tablolarin senaryosu.
        Tablo tablo = tabloService.createTablo("urun16", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, false), new KolonTanimi("kolonb", "text", null, false)));
        assertEquals(List.of(), realPrimaryKeyColumns("urun16"));
        Long aId = tablo.getKolonlar().get(0).getId();

        tabloService.changeKolonPrimaryKey(tablo.getId(), aId, true);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun16"));
    }

    @Test
    void changeKolonPrimaryKey_sameValue_isNoOp() {
        Tablo tablo = tabloService.createTablo("urun17", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true)));
        Long aId = tablo.getKolonlar().get(0).getId();

        tabloService.changeKolonPrimaryKey(tablo.getId(), aId, true);

        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun17"));
    }

    @Test
    void changeKolonPrimaryKey_unknownKolon_isNotFound() {
        Tablo tablo = tabloService.createTablo("urun18", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, false)));

        assertThrows(NotFoundException.class,
                () -> tabloService.changeKolonPrimaryKey(tablo.getId(), -1L, true));
    }

    @Test
    void changeKolonPrimaryKey_columnWithNullValues_isRejectedAndLeavesMetadataUnchanged() {
        // PRIMARY KEY kolonlari Postgres tarafindan otomatik NOT NULL yapilir; mevcut satirlarda
        // NULL varsa DDL patlar. Onemli olan: metadata'daki isaretin de geri alinmasi.
        Tablo tablo = tabloService.createTablo("urun19", testSchemaId,
                List.of(new KolonTanimi("kolona", "text", null, true), new KolonTanimi("kolonb", "text", null, false)));
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"urun19\" (kolona) VALUES ('x')");
        Long bId = tablo.getKolonlar().get(1).getId();

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> tabloService.changeKolonPrimaryKey(tablo.getId(), bId, true));

        // Gercek PK degismedi ve metadata da eski halinde (transaction geri alindi).
        assertEquals(List.of("kolona"), realPrimaryKeyColumns("urun19"));
        assertFalse(tabloService.getTablo(tablo.getId()).getKolonlar().get(1).isPrimaryKey());
    }

    @Test
    void createTablo_noSchemaId_isRejected() {
        // Eskiden bu durumda tablo sessizce "public"e kuruluyordu. "public" gizlendigi icin boyle
        // bir tablo olusturuldugu anda arayuzde gorunmez olurdu — o yuzden artik hata veriyoruz.
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("urun6", null, List.of(new KolonTanimi("ad", "text", null))));

        assertFalse(realTableExists("public", "urun6"));
    }

    @Test
    void createTablo_intoPublicSchema_isNotFound() {
        Schema legacyPublic = schemaRepository.save(new Schema(SchemaService.RESERVED_SCHEMA_NAME));
        try {
            assertThrows(NotFoundException.class, () -> tabloService.createTablo(
                    "urun6b", legacyPublic.getId(), List.of(new KolonTanimi("ad", "text", null))));

            assertFalse(realTableExists("public", "urun6b"));
        } finally {
            schemaRepository.delete(legacyPublic);
        }
    }

    @Test
    void createTablo_withSchemaId_createsRealTableInThatSchema() {
        Schema schema = schemaService.createSchema("raporlama1");

        Tablo tablo = tabloService.createTablo("rapor1", schema.getId(),
                List.of(new KolonTanimi("ad", "text", null)));

        assertEquals(schema.getId(), tablo.getSchema().getId());
        assertTrue(realTableExists("raporlama1", "rapor1"));
        assertFalse(realTableExists(TEST_SCHEMA, "rapor1"));
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
        tabloService.createTablo("rapor4", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        List<Tablo> result = tabloService.listTablolarBySchema(schema.getId());

        assertEquals(1, result.size());
        assertEquals(inSchema.getId(), result.get(0).getId());
    }

    @Test
    void changeSchema_movesRealTableToNewSchema() {
        Schema hedefSchema = schemaService.createSchema("hedef1");
        Tablo tablo = tabloService.createTablo("tasinan1", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        Tablo moved = tabloService.changeSchema(tablo.getId(), hedefSchema.getId());

        assertEquals(hedefSchema.getId(), moved.getSchema().getId());
        assertTrue(realTableExists("hedef1", "tasinan1"));
        assertFalse(realTableExists(TEST_SCHEMA, "tasinan1"));
    }

    @Test
    void changeSchema_missingSchemaId_isRejected() {
        Tablo tablo = tabloService.createTablo("tasinan2", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ValidationException.class, () -> tabloService.changeSchema(tablo.getId(), null));

        assertTrue(realTableExists(TEST_SCHEMA, "tasinan2"));
    }

    @Test
    void changeSchema_unknownSchemaId_isNotFound() {
        Tablo tablo = tabloService.createTablo("tasinan3", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(NotFoundException.class, () -> tabloService.changeSchema(tablo.getId(), -1L));

        assertTrue(realTableExists(TEST_SCHEMA, "tasinan3"));
    }

    @Test
    void changeSchema_sameSchema_isNoopAndDoesNotError() {
        Tablo tablo = tabloService.createTablo("tasinan4", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));
        Long ayniSchemaId = tablo.getSchema().getId();

        Tablo result = tabloService.changeSchema(tablo.getId(), ayniSchemaId);

        assertEquals(ayniSchemaId, result.getSchema().getId());
        assertTrue(realTableExists(TEST_SCHEMA, "tasinan4"));
    }

    // ---------------------------------------------------------------------
    // applyChanges — "Kaydet'e basinca hepsi birden gitsin" akisi.

    @Test
    void applyChanges_renameAddDeleteUpdateAndMoveSchema_allApplyTogether() {
        Tablo tablo = tabloService.createTablo("toplu1", testSchemaId, List.of(
                new KolonTanimi("silinecek", "text", null, false),
                new KolonTanimi("guncellenecek", "text", null, false)));
        Long silinecekId = tablo.getKolonlar().get(0).getId();
        Long guncellenecekId = tablo.getKolonlar().get(1).getId();
        Tag tag = tagRepository.save(new Tag("toplu_tag"));
        Schema ikinciSchema = schemaService.createSchema("toplu_hedef_sema");

        Tablo sonuc = tabloService.applyChanges(tablo.getId(), "toplu1_yeni", ikinciSchema.getId(),
                List.of(silinecekId),
                List.of(new KolonTanimi("yeni_kolon", "numeric", null, true)),
                List.of(new KolonGuncelleme(guncellenecekId, "guncellendi", tag.getId(), true)));

        assertEquals("toplu1_yeni", sonuc.getName());
        assertEquals(ikinciSchema.getId(), sonuc.getSchema().getId());
        assertTrue(realTableExists("toplu_hedef_sema", "toplu1_yeni"));
        assertFalse(realColumnExists("toplu1_yeni", "silinecek"));
        List<String> kolonAdlari = sonuc.getKolonlar().stream().map(Kolon::getName).toList();
        assertTrue(kolonAdlari.contains("guncellendi"));
        assertTrue(kolonAdlari.contains("yeni_kolon"));
        Kolon guncellenenKolon = sonuc.getKolonlar().stream()
                .filter(k -> k.getId().equals(guncellenecekId)).findFirst().orElseThrow();
        assertEquals(tag.getId(), guncellenenKolon.getTag().getId());
        assertTrue(guncellenenKolon.isPrimaryKey());
    }

    @Test
    void applyChanges_newColumnsWithDuplicateNameInSameRequest_isRejectedByExistingAddKolonCheck() {
        // Ayri bir "batch icinde tekrar eden isim" kontrolu yazmadik: addKolon zaten her yeni
        // kolonu tek tek kaydettigi icin, listedeki ikinci "ayniisim" kolonu addKolon'un kendi
        // existsByTabloAndName kontrolune (birincisi az once kaydedildigi icin) takilir.
        Tablo tablo = tabloService.createTablo("toplu2", testSchemaId, List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ConflictException.class, () -> tabloService.applyChanges(tablo.getId(), null, null,
                List.of(),
                List.of(new KolonTanimi("ayniisim", "text", null), new KolonTanimi("ayniisim", "text", null)),
                List.of()));
    }

    @Test
    void applyChanges_unknownTablo_isNotFound() {
        assertThrows(NotFoundException.class,
                () -> tabloService.applyChanges(-1L, "yeni_isim", null, List.of(), List.of(), List.of()));
    }

    /**
     * En kritik test: N alt-islemden biri (burada bir kolonu PK yapmak, NULL deger yuzunden)
     * patlarsa, o ana kadar uygulanmis GORUNEN hicbir sey (rename, yeni kolon, silinen kolon)
     * kalici olmamali — hepsi ayni transaction'da, hepsi ya da hicbiri.
     */
    @Test
    void applyChanges_whenOneStepFails_rollsBackEveryOtherStepToo() {
        Tablo tablo = tabloService.createTablo("toplu3", testSchemaId, List.of(
                new KolonTanimi("nullolan", "text", null, false),
                new KolonTanimi("silinecek", "text", null, false)));
        Long nullolanId = tablo.getKolonlar().get(0).getId();
        Long silinecekId = tablo.getKolonlar().get(1).getId();
        jdbcTemplate.update("INSERT INTO \"" + TEST_SCHEMA + "\".\"toplu3\" (nullolan) VALUES (NULL)");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> tabloService.applyChanges(tablo.getId(), "toplu3_yeni_isim", null,
                        List.of(silinecekId),
                        List.of(new KolonTanimi("yeni_kolon", "text", null)),
                        List.of(new KolonGuncelleme(nullolanId, "nullolan", null, true))));

        // Rename, silme ve ekleme GORUNURDE onceki adimlarda basarili olmus olabilir ama hicbiri
        // kalici degil: hem metadata hem gercek tablo eski haliyle duruyor.
        assertFalse(realTableExists(TEST_SCHEMA, "toplu3_yeni_isim"));
        assertTrue(realTableExists(TEST_SCHEMA, "toplu3"));
        assertTrue(realColumnExists("toplu3", "silinecek"));
        assertFalse(realColumnExists("toplu3", "yeni_kolon"));
        Tablo guncelHali = tabloService.getTablo(tablo.getId());
        assertEquals("toplu3", guncelHali.getName());
        assertEquals(2, guncelHali.getKolonlar().size());
        assertEquals(List.of(), realPrimaryKeyColumns("toplu3"));
    }
}
