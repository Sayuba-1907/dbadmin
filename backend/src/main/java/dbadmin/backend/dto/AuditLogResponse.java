package dbadmin.backend.dto;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.TargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Bir {@link AuditLog} satirinin disariya donen hali — entity dogrudan donulmuyor, projedeki genel konvansiyon. */
public record AuditLogResponse(
        @Schema(description = "Audit satirinin id'si.", example = "1") Long id,
        @Schema(description = "Islemi yapan kullanicinin id'si. Kullanici o zamandan beri silinmis olabilir; "
                + "uygulama acilirken sistemin kendisi yaptiysa (ör. ilk ADMIN'in olusturulmasi) null olur.",
                example = "1") Long userId,
        @Schema(description = "Islemi yapan kullanicinin adi (kullanici sonradan silinse bile kalir).",
                example = "admin") String username,
        @Schema(description = "Ne yapildi.", example = "TABLE_CREATED") OperationType operationType,
        @Schema(description = "Hangi turden bir varlik hakkinda.", example = "TABLE") TargetType targetType,
        @Schema(description = "Hedef varligin id'si.", example = "5") Long targetId,
        @Schema(description = "Serbest metin detay.", example = "tablo olusturuldu: ogrenciler (schema=okul)")
                String detail,
        @Schema(description = "OTel trace'i implemente edildiyse ilgili trace'in id'si; degilse null.")
                String traceId,
        @Schema(description = "Ne zaman olustu.") Instant createdAt) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
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
