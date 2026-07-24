package dbadmin.backend.dto;

/** POST /api/tags icin request govdesi: sadece yeni etiketin adi. */
public record CreateTagRequest(String name) {
}
