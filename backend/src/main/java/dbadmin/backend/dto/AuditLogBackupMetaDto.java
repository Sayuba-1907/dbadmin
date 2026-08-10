package dbadmin.backend.dto;

import java.time.Instant;

/**
 * Yedek dosyasinin icine gomulen meta blok (Req-2.4.3): "kim, ne zaman, kac satir yedekledi".
 * Bilerek bir {@link dbadmin.backend.entity.AuditLog} satiri olarak DB'ye YAZILMAZ — bir
 * sonraki adimda tablo cutoff'a kadar temizlenince "kim temizledi" bilgisi de silinmis olurdu.
 */
public record AuditLogBackupMetaDto(String backedUpBy, Instant backedUpAt, int rowCount) {
}
