package dbadmin.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Tablo sahibine, sahibi olmadigi bir tabloda baskasinin yaptigi bir mutasyon icin dusen kalici
 * bildirim — bkz. requirement-websocket-notifications.md Req-2.3.
 * <p>
 * {@link #tableId} bilerek {@code @ManyToOne} degil duz bir {@code Long}: tablo silinse bile
 * ("tablo silindi" bildirimi tam da bu ani anlatir) bildirim satiri okunabilir kalmali,
 * {@link #tableName} da ayni sebeple denormalize edilir (bkz. {@link AuditLog}'daki
 * userId/username ile ayni gerekce).
 * <p>
 * {@link #isRead}, {@code AuditLog}'un aksine degistirilebilir — bildirim merkezinde
 * okundu/okunmadi durumu kullanicinin etkilesimiyle degisir (bkz. Req-2.7).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "triggered_by_username", nullable = false)
    private String triggeredByUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected Notification() {
    }

    public Notification(
            Long recipientUserId,
            Long tableId,
            String tableName,
            String triggeredByUsername,
            NotificationType type,
            String message) {
        this.recipientUserId = recipientUserId;
        this.tableId = tableId;
        this.tableName = tableName;
        this.triggeredByUsername = triggeredByUsername;
        this.type = type;
        this.message = message;
        this.isRead = false;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public Long getTableId() {
        return tableId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTriggeredByUsername() {
        return triggeredByUsername;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Sadece id'ye gore esitlik: kaydedilmemis (id=null) nesneler asla birbirine esit sayilmaz. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    /** Sabit deger: id degisebildigi icin id'yi hashCode'a katmiyoruz. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
