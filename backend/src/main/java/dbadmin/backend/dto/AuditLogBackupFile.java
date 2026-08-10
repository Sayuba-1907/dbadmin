package dbadmin.backend.dto;

import java.util.List;

/** MinIO'ya yazilan tek JSON dosyasinin tam govdesi — Req-3.5: tek seferlik toplu dump, parca parca append degil. */
public record AuditLogBackupFile(AuditLogBackupMetaDto meta, List<AuditLogBackupEntryDto> entries) {
}
