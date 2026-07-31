package dbadmin.backend.ddl;

import dbadmin.backend.exception.ValidationException;
import org.jspecify.annotations.NonNull;

/**
 * Kolon tipleri icin izin verilen sabit liste (whitelist) + her birinin gercek Postgres
 * karsiligi. "datetime" diye bir Postgres tipi yok, o yuzden bu mapping katmani var
 * (datetime -> timestamp). Metadata'da ({@code Kolon.type}) hangi deger tutuluyorsa,
 * gercek {@code CREATE TABLE} de o degerin postgresType()'ini kullanir.
 */
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

    /**
     * String'i (mesela request body'den gelen "numeric") enum degerine cevirir.
     * UI'daki dropdown zaten secimi sinirlasa da backend client'a guvenmez —
     * whitelist disi bir deger gelirse burada reddedilir (defense in depth).
     */
    public static @NonNull ColumnType fromMetadataValue(String value) {
        for (ColumnType type : values()) {
            if (type.metadataValue.equals(value)) {
                return type;
            }
        }
        throw new ValidationException(
                "VALIDATION_INVALID_COLUMN_TYPE", "type must be one of: numeric, text, datetime, boolean");
    }
}
