package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Zil ikonundaki sayac icin — bkz. requirement-websocket-notifications.md Req-2.5. */
public record UnreadCountResponse(
        @Schema(description = "Okunmamis bildirim sayisi.", example = "3") long count) {
}
