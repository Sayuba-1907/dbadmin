package dbadmin.backend.dto;

/** PATCH /api/tablolar/{id}/schema icin request govdesi: tabloyu bu schema'ya tasi. */
public record ChangeTabloSchemaRequest(Long schemaId) {
}
