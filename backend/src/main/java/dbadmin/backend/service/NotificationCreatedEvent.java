package dbadmin.backend.service;

import dbadmin.backend.entity.NotificationType;
import java.time.Instant;

/**
 * {@link NotificationService#create} bir {@link dbadmin.backend.entity.Notification} kaydettikten sonra
 * yayinlar. {@code NotificationPushListener} bunu {@code @TransactionalEventListener(phase =
 * AFTER_COMMIT)} ile dinler (bkz. requirement-websocket-notifications.md Req-3.5) — WebSocket
 * push'u ancak transaction commit olduktan sonra tetiklenmeli, aksi halde rollback olabilecek bir
 * islem icin "yalan" bir bildirim gonderilmis olur.
 * <p>
 * Gosterim icin gereken tum alanlar (sadece id degil) bilerek burada tasinir: dinleyici
 * AFTER_COMMIT'te calisirken orijinal transaction/entity artik erisilebilir degil, DB'ye tekrar
 * gitmemek icin olusturma anindaki degerler event'in icinde tasinir.
 */
public record NotificationCreatedEvent(
        Long notificationId,
        Long recipientUserId,
        Long tableId,
        String tableName,
        String triggeredByUsername,
        NotificationType type,
        String message,
        Instant createdAt) {
}
