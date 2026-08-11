package dbadmin.backend.dto;

/** {@code GET /api/auth/me/avatar} icin ham dosya icerigi + Content-Type — JSON'a sarilmaz. */
public record AvatarContent(byte[] content, String contentType) {
}
