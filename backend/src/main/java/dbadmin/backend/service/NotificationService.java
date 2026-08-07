package dbadmin.backend.service;

import dbadmin.backend.entity.DataTable;
import dbadmin.backend.entity.Notification;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.NotificationRepository;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablo sahipligi temelli, hedefli bildirimlerin yazildigi ve okundugu tek nokta — bkz.
 * requirement-websocket-notifications.md. {@link TableService}'in mutasyon metodlari {@link
 * #create} cagirir; DB satiri {@code AuditLogService} ile ayni ilkeyle (cagiranin transaction'ina
 * katilir, fail-closed) yazilir. WebSocket push'u ise bu sinifin sorumlulugunda degil: {@link
 * #create} sadece bir {@link NotificationCreatedEvent} yayinlar, push'u commit sonrasi calisan
 * ayri bir dinleyici yapar (bkz. Faz 3/NotificationPushListener) — boylece DB yazimi (garanti) ile
 * anlik bildirim (best-effort) birbirinden ayrisir (Req-3.4).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository, ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
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
    public void create(DataTable table, Long triggeredByUserId, String triggeredByUsername,
            NotificationType type, String message) {
        Long ownerId = table.getCreatedByUserId();
        if (ownerId == null || triggeredByUserId == null || triggeredByUserId.equals(ownerId)) {
            return;
        }
        Notification notification = new Notification(
                ownerId, table.getId(), table.getName(), triggeredByUsername, type, message);
        Notification saved = notificationRepository.save(notification);
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                saved.getId(), ownerId, saved.getTableId(), saved.getTableName(),
                saved.getTriggeredByUsername(), saved.getType(), saved.getMessage(), saved.getCreatedAt()));
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
