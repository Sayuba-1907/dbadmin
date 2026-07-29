package dbadmin.backend.dto;

import dbadmin.backend.entity.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code PATCH /api/kullanicilar/{id}/rol} istek govdesi — sadece ADMIN cagirabilir. */
public record ChangeRolRequest(
        @Schema(description = "Kullanicinin yeni rolu.", example = "EDITOR",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Rol rol) {
}
