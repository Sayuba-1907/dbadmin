package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code PATCH /api/auth/me/password} govdesi. */
public record ChangePasswordRequest(
        @Schema(description = "Mevcut parola — kimligini kanitlamak icin (JWT zaten kimlik dogruluyor, "
                + "ama yaninda birinin acik oturumdan sifre degistirmesini engellemek icin ayrica istenir.")
                String currentPassword,
        @Schema(description = "Yeni parola, en az 8 karakter.") String newPassword) {
}
