package dbadmin.backend.dto;

import dbadmin.backend.security.SessionService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** {@code GET /api/auth/sessions} — "Aktif Oturumlar" ekraninin bir satiri. */
public record SessionResponse(
        @Schema(description = "Oturumu benzersiz tanimlayan kimlik (JWT'nin jti'si).") String jti,
        @Schema(description = "Girisin yapildigi zaman.") Instant issuedAt,
        @Schema(description = "Tarayicinin User-Agent basligi — hangi cihaz/tarayici oldugunu tahmin etmek icin.")
                String userAgent,
        @Schema(description = "true ise bu, isteği yapan tarayicinin KENDI oturumu.") boolean current) {

    public static SessionResponse from(SessionService.SessionInfo session, boolean current) {
        return new SessionResponse(session.jti(), session.issuedAt(), session.userAgent(), current);
    }
}
