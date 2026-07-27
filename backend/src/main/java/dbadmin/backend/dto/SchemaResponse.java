package dbadmin.backend.dto;

import dbadmin.backend.entity.Schema;

/** API'nin disari verdigi Schema govdesi — {@link TabloResponse} ile ayni mantik. */
public record SchemaResponse(Long id, String name) {

    public static SchemaResponse from(Schema schema) {
        return new SchemaResponse(schema.getId(), schema.getName());
    }
}
