package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Kolon;
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
    private JdbcTemplate jdbcTemplate;

    private boolean realTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                Integer.class, tableName);
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
        Tablo tablo = tabloService.createTablo("ogrenci1",
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("yas", "numeric", null)));

        assertTrue(realTableExists("ogrenci1"));
        assertTrue(realColumnExists("ogrenci1", "ad"));
        assertTrue(realColumnExists("ogrenci1", "yas"));
        assertEquals(2, tablo.getKolonlar().size());
    }

    @Test
    void createTablo_duplicateName_isConflict() {
        tabloService.createTablo("ogrenci2", List.of(new KolonTanimi("ad", "text", null)));

        assertThrows(ConflictException.class,
                () -> tabloService.createTablo("ogrenci2", List.of(new KolonTanimi("baska", "text", null))));
    }

    @Test
    void createTablo_invalidTableName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("Buyuk", List.of(new KolonTanimi("ad", "text", null))));

        assertFalse(realTableExists("Buyuk"));
        assertFalse(realTableExists("buyuk"));
    }

    @Test
    void createTablo_invalidColumnType_isRejected() {
        assertThrows(ValidationException.class,
                () -> tabloService.createTablo("ogrenci3", List.of(new KolonTanimi("ad", "varchar", null))));

        assertFalse(realTableExists("ogrenci3"));
    }

    @Test
    void renameTablo_renamesRealTable() {
        Tablo tablo = tabloService.createTablo("kurs1", List.of(new KolonTanimi("ad", "text", null)));

        tabloService.renameTablo(tablo.getId(), "kurs1_yeni");

        assertFalse(realTableExists("kurs1"));
        assertTrue(realTableExists("kurs1_yeni"));
    }

    @Test
    void deleteTablo_dropsRealTableAndCascadesColumns() {
        Tablo tablo = tabloService.createTablo("kurs2",
                List.of(new KolonTanimi("ad", "text", null), new KolonTanimi("kontenjan", "numeric", null)));
        Long kolonId = tablo.getKolonlar().get(0).getId();

        tabloService.deleteTablo(tablo.getId());

        assertFalse(realTableExists("kurs2"));
        assertTrue(kolonRepository.findById(kolonId).isEmpty(), "kolon metadata should be gone with its table");
    }

    @Test
    void addKolon_addsRealColumn() {
        Tablo tablo = tabloService.createTablo("urun1", List.of(new KolonTanimi("ad", "text", null)));

        tabloService.addKolon(tablo.getId(), new KolonTanimi("fiyat", "numeric", null));

        assertTrue(realColumnExists("urun1", "fiyat"));
    }

    @Test
    void deleteKolon_dropsRealColumnButKeepsTag() {
        Tag tag = tagRepository.save(new Tag("onemli"));
        Tablo tablo = tabloService.createTablo("urun2", List.of(new KolonTanimi("ad", "text", tag.getId())));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.deleteKolon(tablo.getId(), kolon.getId());

        assertFalse(realColumnExists("urun2", "ad"));
        assertTrue(tagRepository.findById(tag.getId()).isPresent(), "tag must survive its column being deleted");
    }

    @Test
    void renameKolon_renamesRealColumn() {
        Tablo tablo = tabloService.createTablo("urun3", List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        tabloService.renameKolon(tablo.getId(), kolon.getId(), "isim");

        assertFalse(realColumnExists("urun3", "ad"));
        assertTrue(realColumnExists("urun3", "isim"));
    }

    @Test
    void changeKolonTag_updatesReferenceWithoutTouchingRealTable() {
        Tag tag = tagRepository.save(new Tag("etiket1"));
        Tablo tablo = tabloService.createTablo("urun4", List.of(new KolonTanimi("ad", "text", null)));
        Kolon kolon = tablo.getKolonlar().get(0);

        Kolon updated = tabloService.changeKolonTag(tablo.getId(), kolon.getId(), tag.getId());

        assertEquals(tag.getId(), updated.getTag().getId());
        assertTrue(realColumnExists("urun4", "ad"));
    }

    @Test
    void getTablo_unknownId_isNotFound() {
        assertThrows(NotFoundException.class, () -> tabloService.getTablo(-1L));
    }
}
