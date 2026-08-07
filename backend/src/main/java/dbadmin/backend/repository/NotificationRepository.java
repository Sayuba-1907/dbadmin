package dbadmin.backend.repository;

import dbadmin.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Bildirim merkezi (Req-2.6): sadece isteyenin kendi bildirimleri, en yeni once. */
    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    /** Zil ikonundaki sayac (Req-2.5/2.6): ilk yuklemede bir kez cekilir, sonrasi WebSocket push ile yerelde guncellenir. */
    long countByRecipientUserIdAndIsReadFalse(Long recipientUserId);
}
