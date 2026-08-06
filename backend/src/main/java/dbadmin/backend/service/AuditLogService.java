package dbadmin.backend.service;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.HedefTip;
import dbadmin.backend.entity.IslemTipi;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.KullaniciRepository;
import dbadmin.backend.security.KullaniciRolCacheService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kalici audit kaydi yazan tek nokta — bkz. {@code requirement-audit-log.md}. Cagiran servis
 * metodunun kendi {@code @Transactional}'i icinde cagrilmasi beklenir (Req-2.3): bu sinif kendi
 * transaction'ini acmaz, mevcut olana katilir.
 * <p>
 * Redis'teki fail-open'in ({@link KullaniciRolCacheService}) TAM TERSINE, burada bilerek hicbir
 * try-catch yok: {@code auditLogRepository.save} hata firlatirsa dogal olarak yukari cikar ve
 * cagiranin transaction'ini rollback eder (Req-3.1, fail-closed) — kalici bir islem audit'siz
 * kalmamali.
 * <p>
 * Kullanici id'sini bulmak icin bilerek {@code KullaniciService} degil dogrudan
 * {@link KullaniciRepository} kullanilir: {@code KullaniciService} de bu sinifi kullanacagi icin
 * (kullanici olusturma/rol degistirme/silme de audit'leniyor), {@code KullaniciService} kullansaydik
 * {@code KullaniciService -> AuditLogService -> KullaniciService} dongusu olusurdu (bkz.
 * DECISIONS.md'deki PasswordEncoder ornegiyle ayni kok-neden cozumu, {@code allow-circular-references}
 * gibi bir kacis yoluna gidilmedi).
 */
@Service
public class AuditLogService {

    /**
     * {@code KullaniciSeeder} uygulama aciliminda (henuz hicbir HTTP istegi/kimlik yokken) ilk
     * ADMIN hesabini olustururken bu servisi cagirir — o an gercek bir kullanicinin yaptigi bir
     * islem yok, bilerek "system" olarak isaretleniyor (hata degil, gercek bir senaryo).
     */
    static final String SISTEM_KULLANICISI = "system";

    private final AuditLogRepository auditLogRepository;
    private final KullaniciRolCacheService kullaniciRolCacheService;
    private final KullaniciRepository kullaniciRepository;
    private final Tracer tracer;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            KullaniciRolCacheService kullaniciRolCacheService,
            KullaniciRepository kullaniciRepository,
            Tracer tracer) {
        this.auditLogRepository = auditLogRepository;
        this.kullaniciRolCacheService = kullaniciRolCacheService;
        this.kullaniciRepository = kullaniciRepository;
        this.tracer = tracer;
    }

    public void kaydet(IslemTipi islemTipi, HedefTip hedefTip, Long hedefId, String detay) {
        String kullaniciAdi = aktifKullaniciAdi();
        Long kullaniciId = kullaniciIdBul(kullaniciAdi);
        String traceId = aktifTraceId();

        auditLogRepository.save(new AuditLog(
                kullaniciId, kullaniciAdi, islemTipi, hedefTip, hedefId, detay, traceId));
    }

    /** {@code GET /api/audit-loglar} icin — hepsi opsiyonel filtreler, bkz. AuditLogRepository#ara. */
    @Transactional(readOnly = true)
    public Page<AuditLog> ara(Long kullaniciId, HedefTip hedefTip, Instant bas, Instant bit, Pageable pageable) {
        return auditLogRepository.ara(kullaniciId, hedefTip, bas, bit, pageable);
    }

    private String aktifKullaniciAdi() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : SISTEM_KULLANICISI;
    }

    /**
     * Id'yi once rol cache'inden okur — bu istek zaten {@code JwtAuthenticationFilter} tarafindan
     * doldurulmus/okunmus oldugu icin pratikte hep cache hit olur, ekstra DB sorgusu gerekmez.
     * Cache miss/erisilemez ihtimaline karsi DB'ye duser. {@link #SISTEM_KULLANICISI} icin DB'de
     * hicbir zaman bir satir olmayacagi icin arama yapilmadan direkt null donulur.
     */
    private Long kullaniciIdBul(String kullaniciAdi) {
        if (SISTEM_KULLANICISI.equals(kullaniciAdi)) {
            return null;
        }
        return kullaniciRolCacheService.get(kullaniciAdi)
                .map(KullaniciRolCacheService.OnbellekliKullanici::id)
                .orElseGet(() -> kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                        .orElseThrow(() -> new NotFoundException(
                                "NOT_FOUND_USER", "user not found: " + kullaniciAdi,
                                Map.of("id", kullaniciAdi)))
                        .getId());
    }

    /** OTel bu projede her zaman kurulu oldugu icin {@link Tracer} dogrudan enjekte ediliyor (bkz. SpanRunner ile ayni yaklasim); aktif span yoksa null doner. */
    private String aktifTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }
}
