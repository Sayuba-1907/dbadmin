package dbadmin.backend.websocket;

import dbadmin.backend.config.RabbitConfig;
import dbadmin.backend.service.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link dbadmin.backend.service.NotificationService#create}'in transaction commit sonrasi
 * RabbitMQ'ya publish ettigi {@link NotificationMessage}'i {@link RabbitConfig#NOTIFICATION_QUEUE}
 * kuyrugundan tuketip {@link WebSocketSessionRegistry} uzerinden hedefli push yapar — bkz.
 * requirement-websocket-notifications.md Req-2.4/Req-3.5, backend/notlar "RabbitMQ koyulacak
 * notification icin".
 * <p>
 * Bu sinif kasitli olarak {@code NotificationService}'ten (publish) ve DB yazimindan tamamen
 * bagimsiz: kuyruktan gelen mesaj disinda hicbir baglama erismez. Bir istisna firlatirsa
 * {@code spring.rabbitmq.listener.simple.retry.*} konfigurasyonu birkac kez yeniden dener; retry
 * tukenirse mesaj (queue'nun {@code x-dead-letter-exchange} argumani sayesinde) otomatik olarak
 * DLQ'ya duser — WebSocket push'u kaybolmaz, sadece anlik degil incelenebilir hale gelir.
 */
@Component
public class RabbitNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotificationListener.class);

    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public RabbitNotificationListener(WebSocketSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void push(NotificationMessage message) {
        String json = objectMapper.writeValueAsString(new Payload(
                message.notificationId(), message.tableId(), message.tableName(),
                message.triggeredByUsername(), message.type().name(), message.message(),
                message.createdAt()));
        registry.send(message.recipientUserId(), json);
    }

    /** Frontend'e gidecek JSON govdesi — {@code Notification} entity'sinin degil, mesajin alanlarindan kurulur. */
    private record Payload(Long id, Long tableId, String tableName, String triggeredByUsername,
            String type, String message, java.time.Instant createdAt) {
    }
}
