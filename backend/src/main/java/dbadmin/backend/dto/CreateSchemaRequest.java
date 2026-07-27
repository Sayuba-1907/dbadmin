package dbadmin.backend.dto;

/** POST /api/schemalar icin request govdesi: "bu isimde schema kur". */
public record CreateSchemaRequest(String name) {
}
