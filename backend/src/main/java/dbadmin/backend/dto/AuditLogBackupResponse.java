package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** {@code POST /api/maintenance/audit-logs/backup}'in donus govdesi. */
public record AuditLogBackupResponse(
        @Schema(description = "MinIO'daki nesnenin anahtari.", example = "backup-2026-08-10T11-31-00Z.json")
                String key,
        @Schema(description = "Yedeklenen (ve tablodan silinen) satir sayisi.", example = "49") int rowCount,
        @Schema(description = "Yedeklemenin yapildigi zaman.") Instant backedUpAt) {
}
