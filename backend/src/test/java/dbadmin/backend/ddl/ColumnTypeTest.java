package dbadmin.backend.ddl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;

class ColumnTypeTest {

    @Test
    void numericMapsToItself() {
        assertEquals("numeric", ColumnType.fromMetadataValue("numeric").postgresType());
    }

    @Test
    void textMapsToItself() {
        assertEquals("text", ColumnType.fromMetadataValue("text").postgresType());
    }

    @Test
    void booleanMapsToItself() {
        assertEquals("boolean", ColumnType.fromMetadataValue("boolean").postgresType());
    }

    @Test
    void datetimeMapsToTimestamp() {
        // "datetime" has no matching Postgres type name, hence the mapping layer.
        assertEquals("timestamp", ColumnType.fromMetadataValue("datetime").postgresType());
    }

    @Test
    void rejectsValueOutsideWhitelist() {
        assertThrows(ValidationException.class, () -> ColumnType.fromMetadataValue("varchar"));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> ColumnType.fromMetadataValue(null));
    }
}
