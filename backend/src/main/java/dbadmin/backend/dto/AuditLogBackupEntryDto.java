package dbadmin.backend.dto;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.TargetType;
import java.time.Instant;

/**
 * Bir {@link AuditLog} satirinin MinIO'ya yazilan yedek dosyasindaki hali —
 * requirement-maintenance-audit-backup.md Req-2.4.2: {@code id} bilerek YOK, DB'ye ozgu
 * (sequence kaynakli) bir deger, dosyaya tasindiktan sonra is anlami tasimiyor.
 */
public record AuditLogBackupEntryDto(
        Long userId,
        String username,
        OperationType operationType,
        TargetType targetType,
        Long targetId,
        String detail,
        String traceId,
        Instant createdAt) {

    public static AuditLogBackupEntryDto from(AuditLog auditLog) {
        return new AuditLogBackupEntryDto(
                auditLog.getUserId(),
                auditLog.getUsername(),
                auditLog.getOperationType(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getDetail(),
                auditLog.getTraceId(),
                auditLog.getCreatedAt());
    }
}
