package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// TabloServiceIntegrationTest ile ayni mantik: gercek Testcontainers Postgres'e karsi
// calisir, hem metadata (Schema satiri) hem gercek DB (information_schema.schemata) kontrol edilir.
class SchemaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean realSchemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        return count != null && count > 0;
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
}
