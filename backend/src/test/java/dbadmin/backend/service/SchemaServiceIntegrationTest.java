package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TabloRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// TabloServiceIntegrationTest ile ayni mantik: gercek Testcontainers Postgres'e karsi
// calisir, hem metadata (Schema satiri) hem gercek DB (information_schema.schemata) kontrol edilir.
class SchemaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private TabloService tabloService;

    @Autowired
    private TabloRepository tabloRepository;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean realSchemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        return count != null && count > 0;
    }

    /**
     * Uygulama artik "public" adinda bir Schema satiri olusturmuyor. Gizleme mantiginin savunma
     * katmanini test edebilmek icin boyle bir satiri repository uzerinden (service'i, dolayisiyla
     * validasyonu atlayarak) elle yaziyoruz — eski bir kurulumdan kalmis satiri taklit ediyor.
     * Testler ayni Postgres'i paylastigi icin cagiran test satiri sonunda silmeli.
     */
    private Schema insertLegacyPublicRow() {
        return schemaRepository.save(new Schema(SchemaService.RESERVED_SCHEMA_NAME));
    }

    @Test
    void createSchema_createsMetadataAndRealSchema() {
        Schema schema = schemaService.createSchema("raporlama");

        assertTrue(realSchemaExists("raporlama"));
        assertTrue(schema.getId() != null);
    }

    @Test
    void createSchema_duplicateName_isConflict() {
        schemaService.createSchema("arsiv1");

        assertThrows(ConflictException.class, () -> schemaService.createSchema("arsiv1"));
    }

    @Test
    void createSchema_publicName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class, () -> schemaService.createSchema("public"));
        assertThrows(ValidationException.class, () -> schemaService.createSchema("Public"));
    }

    @Test
    void createSchema_invalidName_isRejectedBeforeAnyDdl() {
        assertThrows(ValidationException.class, () -> schemaService.createSchema("Buyuk"));

        assertFalse(realSchemaExists("Buyuk"));
        assertFalse(realSchemaExists("buyuk"));
    }

    @Test
    void deleteSchema_dropsRealSchema() {
        Schema schema = schemaService.createSchema("arsiv2");

        schemaService.deleteSchema(schema.getId());

        assertFalse(realSchemaExists("arsiv2"));
    }

    @Test
    void getSchema_unknownId_isNotFound() {
        assertThrows(NotFoundException.class, () -> schemaService.getSchema(-1L));
    }

    @Test
    void deleteSchema_cascadesTabloMetadataAlongWithRealTables() {
        Schema schema = schemaService.createSchema("arsiv3");
        Tablo tablo = tabloService.createTablo("arsivli1", schema.getId(),
                List.of(new KolonTanimi("ad", "text", null)));

        schemaService.deleteSchema(schema.getId());

        assertFalse(realSchemaExists("arsiv3"));
        assertTrue(tabloRepository.findById(tablo.getId()).isEmpty(),
                "tablo metadata should be gone with its schema, not a ghost entry pointing at a dropped table");
    }

    @Test
    void bootstrap_doesNotCreateAPublicSchemaRow() {
        assertTrue(schemaRepository.findByNameIgnoreCase(SchemaService.RESERVED_SCHEMA_NAME).isEmpty(),
                "'public' altyapiya ait; metadata'da onu temsil eden bir satir hic olmamali");
    }

    @Test
    void listSchemalar_omitsPublic() {
        Schema legacy = insertLegacyPublicRow();
        try {
            assertTrue(schemaService.listSchemalar().stream().noneMatch(s -> s.name().equals("public")),
                    "'public' listede gorunmemeli");
        } finally {
            schemaRepository.delete(legacy);
        }
    }

    @Test
    void deleteSchema_publicSchema_isNotFoundAndDoesNotDropTheRealSchema() {
        Schema legacy = insertLegacyPublicRow();
        try {
            // Gizli oldugu icin "yasak" degil "yok" muamelesi goruyor; onemli olan DROP SCHEMA
            // public CASCADE'in calismamasi — o, uygulamanin kendi metadata tablolarini silerdi.
            assertThrows(NotFoundException.class, () -> schemaService.deleteSchema(legacy.getId()));

            assertTrue(realSchemaExists("public"));
        } finally {
            schemaRepository.delete(legacy);
        }
    }

    @Test
    void renameSchema_renamesMetadataAndRealSchema() {
        Schema schema = schemaService.createSchema("eski_ad1");

        Schema renamed = schemaService.renameSchema(schema.getId(), "yeni_ad1");

        assertEquals("yeni_ad1", renamed.getName());
        assertFalse(realSchemaExists("eski_ad1"));
        assertTrue(realSchemaExists("yeni_ad1"));
    }

    @Test
    void renameSchema_sameName_isNoopAndDoesNotError() {
        Schema schema = schemaService.createSchema("ayni_ad1");

        Schema result = schemaService.renameSchema(schema.getId(), "ayni_ad1");

        assertEquals("ayni_ad1", result.getName());
        assertTrue(realSchemaExists("ayni_ad1"));
    }

    @Test
    void renameSchema_publicSchema_isNotFound() {
        Schema legacy = insertLegacyPublicRow();
        try {
            assertThrows(NotFoundException.class, () -> schemaService.renameSchema(legacy.getId(), "baska_isim"));

            assertTrue(realSchemaExists("public"));
        } finally {
            schemaRepository.delete(legacy);
        }
    }

    @Test
    void renameSchema_toReservedName_isRejected() {
        Schema schema = schemaService.createSchema("eski_ad2");

        assertThrows(ValidationException.class, () -> schemaService.renameSchema(schema.getId(), "public"));

        assertTrue(realSchemaExists("eski_ad2"));
    }

    @Test
    void renameSchema_duplicateName_isConflict() {
        schemaService.createSchema("dolu_isim1");
        Schema schema = schemaService.createSchema("eski_ad3");

        assertThrows(ConflictException.class, () -> schemaService.renameSchema(schema.getId(), "dolu_isim1"));
    }
}
