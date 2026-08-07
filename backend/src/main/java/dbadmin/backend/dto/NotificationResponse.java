package dbadmin.backend.dto;

import dbadmin.backend.entity.Notification;
import dbadmin.backend.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Bir {@link Notification} satirinin disariya donen hali — entity dogrudan donulmuyor, projedeki genel konvansiyon. */
public record NotificationResponse(
        @Schema(description = "Bildirimin id'si.", example = "1") Long id,
        @Schema(description = "Bildirimin hakkinda oldugu tablonun id'si. Tablo sonradan silinmis olabilir; "
                + "bu alan yine de kalir (bkz. Notification.tableId).", example = "5") Long tableId,
        @Schema(description = "Tablonun adi (tablo silinse bile okunabilir kalsin diye denormalize edilir).",
                example = "ogrenciler") String tableName,
        @Schema(description = "Degisikligi yapan kullanicinin adi.", example = "editor1") String triggeredByUsername,
        @Schema(description = "Ne turden bir degisiklik.", example = "COLUMN_ADDED") NotificationType type,
        @Schema(description = "Kullaniciya gosterilecek serbest metin.",
                example = "\"ogrenciler\" tablosuna \"yas\" kolonu eklendi.") String message,
        @Schema(description = "Okundu isaretlenmis mi.") boolean isRead,
        @Schema(description = "Ne zaman olustu.") Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTableId(),
                notification.getTableName(),
                notification.getTriggeredByUsername(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
