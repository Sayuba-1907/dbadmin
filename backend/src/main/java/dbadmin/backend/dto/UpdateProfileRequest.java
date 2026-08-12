package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code PATCH /api/auth/me} govdesi. Her iki alan da opsiyonel — {@code null} olan
 * "degistirme" anlamina gelir, sadece dolu gelen alan guncellenir (bkz. UserService#updateProfile).
 */
public record UpdateProfileRequest(
        @Schema(description = "Yeni ad soyad. Null ise degismez.", example = "Salih Bayraktar")
                String fullName,
        @Schema(description = "Yeni kullanici adi. Null ise degismez.", example = "salih_b")
                String username,
        @Schema(description = "Yeni e-posta. Null ise degismez, bos string temizler.", example = "salih@example.com")
                String email) {
}
