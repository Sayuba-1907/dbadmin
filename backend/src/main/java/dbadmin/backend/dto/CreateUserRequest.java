package dbadmin.backend.dto;

import dbadmin.backend.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/users} istek govdesi — sadece ADMIN cagirabilir. */
public record CreateUserRequest(
        @Schema(description = "Kullanici adi. 3-30 karakter, harf/rakam/alt cizgi.", example = "ayse",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String username,
        @Schema(description = "Duz metin parola, en az 8 karakter. Sunucuda BCrypt ile hash'lenir.",
                        example = "gizliParola1", requiredMode = Schema.RequiredMode.REQUIRED)
                String password,
        @Schema(description = "Rol. Verilmezse VIEWER kabul edilir.", example = "EDITOR") Role role) {
}
