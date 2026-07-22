package dbadmin.backend.ddl;

import dbadmin.backend.exception.ValidationException;
import org.jspecify.annotations.NonNull;

// The whitelist from the spec, plus the mapping to the real PostgreSQL type.
// "datetime" has no matching Postgres type name, hence this mapping layer.
public enum ColumnType {

    NUMERIC("numeric", "numeric"),
    TEXT("text", "text"),
    DATETIME("datetime", "timestamp"),
    BOOLEAN("boolean", "boolean");

    private final String metadataValue;
    private final String postgresType;

    ColumnType(String metadataValue, String postgresType) {
        this.metadataValue = metadataValue;
        this.postgresType = postgresType;
    }

    public String metadataValue() {
        return metadataValue;
    }

    public String postgresType() {
        return postgresType;
    }

    // Never trust the client: this is called even though the UI dropdown already limits the choice.
    public static @NonNull ColumnType fromMetadataValue(String value) {
        for (ColumnType type : values()) {
            if (type.metadataValue.equals(value)) {
                return type;
            }
        }
        throw new ValidationException("type must be one of: numeric, text, datetime, boolean");
    }
}
