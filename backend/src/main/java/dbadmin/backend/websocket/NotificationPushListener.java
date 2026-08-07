package dbadmin.backend.websocket;

import dbadmin.backend.service.NotificationCreatedEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link dbadmin.backend.service.NotificationService#create}'in yayinladigi {@link
 * NotificationCreatedEvent}'i dinleyip {@link WebSocketSessionRegistry} uzerinden hedefli push
 * yapar — bkz. requirement-websocket-notifications.md Req-2.4/Req-3.5.
 * <p>
 * {@code phase = AFTER_COMMIT} bu sinifin var olma sebebi: varsayilan faz (event publish
 * edildigi an, yani hala {@code @Transactional} icindeyken) kullanilsaydi, sonradan rollback
 * olabilecek bir islem icin bile push gonderilmis olurdu. Commit'ten sonra calistigi icin bu
 * metod artik orijinal transaction'in DIŞINDADIR — {@link NotificationCreatedEvent}'in butun
 * gosterim alanlarini kendi icinde tasimasi bu yuzden onemli, aksi halde burada entity'lere
 * tekrar erismek gerekirdi.
 */
@Component
public class NotificationPushListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushListener.class);

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public NotificationPushListener(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void push(NotificationCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(new Payload(
                    event.notificationId(), event.tableId(), event.tableName(),
                    event.triggeredByUsername(), event.type().name(), event.message(),
                    event.createdAt()));
            registry.send(event.recipientUserId(), json);
        } catch (Exception ex) {
            // Fail-open push (Req-3.4): serialize/gonderim hatasi kalici Bildirim kaydini
            // etkilememeli, kullanici sonraki girisinde ilk-yukleme sayacindan zaten gorecek.
            log.warn("Bildirim push edilirken hata (notificationId={}): {}", event.notificationId(), ex.getMessage());
        }
    }

    /** Frontend'e gidecek JSON govdesi — {@code Notification} entity'sinin degil, event'in alanlarindan kurulur. */
    private record Payload(Long id, Long tableId, String tableName, String triggeredByUsername,
            String type, String message, Instant createdAt) {
    }
}
