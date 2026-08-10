package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** {@code GET /api/maintenance/audit-logs/backups} icin — MinIO'daki her yedek dosyasinin kendi meta blogundan okunan ozeti. */
public record AuditLogBackupListItemDto(
        @Schema(description = "MinIO'daki nesnenin anahtari.", example = "backup-2026-08-10T11-31-00Z.json")
                String key,
        @Schema(description = "Yedegi kim aldi.", example = "admin") String backedUpBy,
        @Schema(description = "Ne zaman alindi.") Instant backedUpAt,
        @Schema(description = "Kac satir yedeklendi.", example = "49") int rowCount) {
}
