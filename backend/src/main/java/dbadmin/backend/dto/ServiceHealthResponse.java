package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code GET /api/maintenance/health}'in donus govdesi (Req-2.2) — basit yesil/kirmizi gosterge. */
public record ServiceHealthResponse(
        @Schema(description = "Postgres erisilebilir mi.") boolean postgres,
        @Schema(description = "Redis erisilebilir mi.") boolean redis,
        @Schema(description = "Tempo erisilebilir mi.") boolean tempo,
        @Schema(description = "Loki erisilebilir mi.") boolean loki) {
}
