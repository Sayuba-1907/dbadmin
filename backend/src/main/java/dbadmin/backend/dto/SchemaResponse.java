package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** API'nin disari verdigi Schema govdesi — {@link TabloResponse} ile ayni mantik. */
public record SchemaResponse(
        @Schema(description = "Schema'nin id'si.", example = "1") Long id,
        @Schema(description = "Schema'nin adi.", example = "public") String name) {

    public static SchemaResponse from(dbadmin.backend.entity.Schema schema) {
        return new SchemaResponse(schema.getId(), schema.getName());
    }
}
