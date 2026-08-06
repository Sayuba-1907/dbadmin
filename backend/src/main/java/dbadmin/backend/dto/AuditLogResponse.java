package dbadmin.backend.dto;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.HedefTip;
import dbadmin.backend.entity.IslemTipi;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Bir {@link AuditLog} satirinin disariya donen hali — entity dogrudan donulmuyor, projedeki genel konvansiyon. */
public record AuditLogResponse(
        @Schema(description = "Audit satirinin id'si.", example = "1") Long id,
        @Schema(description = "Islemi yapan kullanicinin id'si. Kullanici o zamandan beri silinmis olabilir; "
                + "uygulama acilirken sistemin kendisi yaptiysa (ör. ilk ADMIN'in olusturulmasi) null olur.",
                example = "1") Long kullaniciId,
        @Schema(description = "Islemi yapan kullanicinin adi (kullanici sonradan silinse bile kalir).",
                example = "admin") String kullaniciAdi,
        @Schema(description = "Ne yapildi.", example = "TABLO_OLUSTURULDU") IslemTipi islemTipi,
        @Schema(description = "Hangi turden bir varlik hakkinda.", example = "TABLO") HedefTip hedefTip,
        @Schema(description = "Hedef varligin id'si.", example = "5") Long hedefId,
        @Schema(description = "Serbest metin detay.", example = "tablo olusturuldu: ogrenciler (schema=okul)")
                String detay,
        @Schema(description = "OTel trace'i implemente edildiyse ilgili trace'in id'si; degilse null.")
                String traceId,
        @Schema(description = "Ne zaman olustu.") Instant olusturulmaZamani) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getKullaniciId(),
                auditLog.getKullaniciAdi(),
                auditLog.getIslemTipi(),
                auditLog.getHedefTip(),
                auditLog.getHedefId(),
                auditLog.getDetay(),
                auditLog.getTraceId(),
                auditLog.getOlusturulmaZamani());
    }
}
