package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** PATCH /api/tables/{id}/schema icin request govdesi: tabloyu bu schema'ya tasi. */
public record ChangeTableSchemaRequest(
        @Schema(
                        description = "Tablonun tasinacagi hedef schema'nin id'si. Bos gecilemez — "
                                + "hangi schema'ya tasinacagi belirtilmeden bu islemin anlami yok.",
                        example = "2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long schemaId) {
}
