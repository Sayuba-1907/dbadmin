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
 * Kim, ne zaman, neyi degistirdi bilgisinin kalici kaydi — bkz. {@code requirement-audit-log.md}.
 * OTel/business log'lardan (Loki, 48 saat retention) farkli olarak bu tablo {@code public}
 * semada, digerleri kadar kalici yasar; UPDATE/DELETE uc noktasi yoktur, sadece INSERT edilir
 * (bkz. Req-2.5).
 * <p>
 * {@link #userId} ve {@link #username} bilerek ikisi birden tutulur: kullanici daha
 * sonra silinse bile ({@code users} tablosundan gercekten kalkarsa) audit kaydinin "kim
 * yapti" bilgisi kullanici adi uzerinden okunabilir kalsin diye — id tek basina, kullanici
 * silindikten sonra hicbir seye isaret etmeyen bir sayidan ibaret kalirdi.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "detail")
    private String detail;

    /** OTel implemente edildiyse aktif trace'in id'si; degilse null (bkz. Req-3.6). */
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected AuditLog() {
    }

    public AuditLog(
            Long userId,
            String username,
            OperationType operationType,
            TargetType targetType,
            Long targetId,
            String detail,
            String traceId) {
        this.userId = userId;
        this.username = username;
        this.operationType = operationType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.traceId = traceId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getTraceId() {
        return traceId;
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
        if (!(o instanceof AuditLog other)) {
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
