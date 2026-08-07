package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/auth/login} istek govdesi. */
public record LoginRequest(
        @Schema(description = "Kullanici adi.", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
                String username,
        @Schema(description = "Duz metin parola. Sunucuda BCrypt hash'iyle karsilastirilir, hicbir yerde saklanmaz.",
                        example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
                String password) {
}
