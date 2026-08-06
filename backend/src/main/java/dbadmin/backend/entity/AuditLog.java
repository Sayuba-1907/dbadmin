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
 * {@link #kullaniciId} ve {@link #kullaniciAdi} bilerek ikisi birden tutulur: kullanici daha
 * sonra silinse bile ({@code kullanici} tablosundan gercekten kalkarsa) audit kaydinin "kim
 * yapti" bilgisi kullanici adi uzerinden okunabilir kalsin diye — id tek basina, kullanici
 * silindikten sonra hicbir seye isaret etmeyen bir sayidan ibaret kalirdi.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_id")
    private Long kullaniciId;

    @Column(name = "kullanici_adi", nullable = false)
    private String kullaniciAdi;

    @Enumerated(EnumType.STRING)
    @Column(name = "islem_tipi", nullable = false)
    private IslemTipi islemTipi;

    @Enumerated(EnumType.STRING)
    @Column(name = "hedef_tip", nullable = false)
    private HedefTip hedefTip;

    @Column(name = "hedef_id", nullable = false)
    private Long hedefId;

    @Column(name = "detay")
    private String detay;

    /** OTel implemente edildiyse aktif trace'in id'si; degilse null (bkz. Req-3.6). */
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "olusturulma_zamani", nullable = false)
    private Instant olusturulmaZamani;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected AuditLog() {
    }

    public AuditLog(
            Long kullaniciId,
            String kullaniciAdi,
            IslemTipi islemTipi,
            HedefTip hedefTip,
            Long hedefId,
            String detay,
            String traceId) {
        this.kullaniciId = kullaniciId;
        this.kullaniciAdi = kullaniciAdi;
        this.islemTipi = islemTipi;
        this.hedefTip = hedefTip;
        this.hedefId = hedefId;
        this.detay = detay;
        this.traceId = traceId;
        this.olusturulmaZamani = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getKullaniciId() {
        return kullaniciId;
    }

    public String getKullaniciAdi() {
        return kullaniciAdi;
    }

    public IslemTipi getIslemTipi() {
        return islemTipi;
    }

    public HedefTip getHedefTip() {
        return hedefTip;
    }

    public Long getHedefId() {
        return hedefId;
    }

    public String getDetay() {
        return detay;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOlusturulmaZamani() {
        return olusturulmaZamani;
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
