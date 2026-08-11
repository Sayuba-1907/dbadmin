package dbadmin.backend.service;

import dbadmin.backend.config.RabbitConfig;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.entity.Notification;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.NotificationRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tablo sahipligi temelli, hedefli bildirimlerin yazildigi ve okundugu tek nokta — bkz.
 * requirement-websocket-notifications.md. {@link TableService}'in mutasyon metodlari {@link
 * #create} cagirir; DB satiri {@code AuditLogService} ile ayni ilkeyle (cagiranin transaction'ina
 * katilir, fail-closed) yazilir. WebSocket push'u ise bu sinifin sorumlulugunda degil: {@link
 * #create} sadece transaction commit olduktan sonra RabbitMQ'ya bir {@link NotificationMessage}
 * publish eder (bkz. backend/notlar "RabbitMQ koyulacak notification icin"), push'u kuyruktan
 * tuketen ayri bir dinleyici yapar (bkz. websocket/RabbitNotificationListener) — boylece DB yazimi
 * (garanti) ile anlik bildirim (best-effort) birbirinden ayrisir (Req-3.4).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final RabbitTemplate rabbitTemplate;

    public NotificationService(NotificationRepository notificationRepository, RabbitTemplate rabbitTemplate) {
        this.notificationRepository = notificationRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Sahibi olmayan biri {@code table}'u etkileyen bir mutasyon yaptiginda cagirilir.
     * Tetikleyen == sahip ise (Req-3.3) veya tablonun henuz sahibi yoksa (backfill'den once
     * kalmis olabilecek bir satir — pratikte olmamali ama savunmaci) sessizce hicbir sey yapmaz.
     *
     * @param triggeredByUserId  islemi yapan aktif kullanicinin id'si
     * @param triggeredByUsername ayni kullanicinin adi — {@link Notification#tableName} gibi mesajda
     *                               gosterilecek metin icin denormalize edilir
     */
    @Transactional
    public void create(DataTable table, Long triggeredByUserId, String triggeredByUsername,
            NotificationType type, String message) {
        Long ownerId = table.getCreatedByUserId();
        if (ownerId == null || triggeredByUserId == null || triggeredByUserId.equals(ownerId)) {
            return;
        }
        Notification notification = new Notification(
                ownerId, table.getId(), table.getName(), triggeredByUsername, type, message);
        Notification saved = notificationRepository.save(notification);
        NotificationMessage notificationMessage = new NotificationMessage(
                saved.getId(), ownerId, saved.getTableId(), saved.getTableName(),
                saved.getTriggeredByUsername(), saved.getType(), saved.getMessage(), saved.getCreatedAt());
        // AFTER_COMMIT: eski @TransactionalEventListener(phase = AFTER_COMMIT) ile ayni sebep
        // (Req-3.5) — rollback olabilecek bir islem icin RabbitMQ'ya "yalan" bir bildirim
        // publish edilmemeli. Publish hatasi (broker erisilemez vs.) fail-open'dir: DB'ye
        // yazilmis Notification satiri kalicidir, kullanici sonraki girisinde ilk-yukleme
        // sayacindan zaten gorecek (Req-3.4).
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.NOTIFICATION_EXCHANGE, RabbitConfig.NOTIFICATION_ROUTING_KEY, notificationMessage);
                } catch (Exception ex) {
                    log.warn("Bildirim RabbitMQ'ya publish edilirken hata (notificationId={}): {}",
                            saved.getId(), ex.getMessage());
                }
            }
        });
    }

    /** Bildirim merkezi (Req-2.6): sadece isteyenin kendi bildirimleri, rol kisiti yok (Req-3.6). */
    @Transactional(readOnly = true)
    public Page<Notification> list(Long recipientUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable);
    }

    /** Zil ikonundaki sayac icin ilk (ve tek) sunucu sorgusu (Req-2.5). */
    @Transactional(readOnly = true)
    public long unreadCount(Long recipientUserId) {
        return notificationRepository.countByRecipientUserIdAndIsReadFalse(recipientUserId);
    }

    /**
     * Tekil okundu isaretleme (Req-2.7). Baska bir kullanicinin bildirimine erisim, id bulunamamis
     * gibi 404 ile reddedilir — {@code recipientUserId} filtresi olmadan sadece {@code id}'ye
     * gore ceksek, baskasinin bildirimini "bulunamadi" yerine gercekten okuyup degistirebilirdik.
     */
    @Transactional
    public void markAsRead(Long id, Long activeUserId) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getRecipientUserId().equals(activeUserId))
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_NOTIFICATION", "notification not found: " + id, Map.of("id", String.valueOf(id))));
        notification.setRead(true);
    }

    /** Tumunu okundu isaretle (Req-2.7) — sadece aktif kullanicinin kendi bildirimleri etkilenir. */
    @Transactional
    public void markAllAsRead(Long activeUserId) {
        Pageable all = Pageable.unpaged();
        notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(activeUserId, all)
                .forEach(n -> n.setRead(true));
    }
}
