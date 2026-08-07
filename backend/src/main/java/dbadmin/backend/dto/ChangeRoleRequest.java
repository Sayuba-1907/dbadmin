package dbadmin.backend.dto;

import dbadmin.backend.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code PATCH /api/users/{id}/role} istek govdesi — sadece ADMIN cagirabilir. */
public record ChangeRoleRequest(
        @Schema(description = "Kullanicinin yeni rolu.", example = "EDITOR",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Role role) {
}
