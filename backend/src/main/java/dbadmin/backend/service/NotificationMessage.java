package dbadmin.backend.service;

import dbadmin.backend.entity.NotificationType;
import java.time.Instant;

/**
 * {@link NotificationService#create} bir {@link dbadmin.backend.entity.Notification} kaydettikten
 * sonra, transaction {@code AFTER_COMMIT} olduktan sonra RabbitMQ'ya bu sekliyle publish eder
 * (bkz. requirement-websocket-notifications.md Req-3.5, backend/notlar "RabbitMQ koyulacak
 * notification icin") — WebSocket push'u ancak transaction commit olduktan sonra tetiklenmeli,
 * aksi halde rollback olabilecek bir islem icin "yalan" bir bildirim gonderilmis olur. Kuyruktan
 * {@code RabbitNotificationListener} tuketir.
 * <p>
 * Gosterim icin gereken tum alanlar (sadece id degil) bilerek burada tasinir: kuyruktan tuketen
 * taraf orijinal transaction/entity'ye erisemez, DB'ye tekrar gitmemek icin olusturma anindaki
 * degerler mesajin icinde tasinir. Record oldugu icin Jackson'in JSON'a/'dan cevirmesi otomatik.
 */
public record NotificationMessage(
        Long notificationId,
        Long recipientUserId,
        Long tableId,
        String tableName,
        String triggeredByUsername,
        NotificationType type,
        String message,
        Instant createdAt) {
}
