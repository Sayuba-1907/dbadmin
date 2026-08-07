package dbadmin.backend.service;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.entity.OperationType;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.security.UserRoleCacheService;
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
 * Redis'teki fail-open'in ({@link UserRoleCacheService}) TAM TERSINE, burada bilerek hicbir
 * try-catch yok: {@code auditLogRepository.save} hata firlatirsa dogal olarak yukari cikar ve
 * cagiranin transaction'ini rollback eder (Req-3.1, fail-closed) — kalici bir islem audit'siz
 * kalmamali.
 * <p>
 * Kullanici id'sini bulmak icin bilerek {@code UserService} degil dogrudan
 * {@link UserRepository} kullanilir: {@code UserService} de bu sinifi kullanacagi icin
 * (kullanici olusturma/rol degistirme/silme de audit'leniyor), {@code UserService} kullansaydik
 * {@code UserService -> AuditLogService -> UserService} dongusu olusurdu (bkz.
 * DECISIONS.md'deki PasswordEncoder ornegiyle ayni kok-neden cozumu, {@code allow-circular-references}
 * gibi bir kacis yoluna gidilmedi).
 */
@Service
public class AuditLogService {

    /**
     * {@code UserSeeder} uygulama aciliminda (henuz hicbir HTTP istegi/kimlik yokken) ilk
     * ADMIN hesabini olustururken bu servisi cagirir — o an gercek bir kullanicinin yaptigi bir
     * islem yok, bilerek "system" olarak isaretleniyor (hata degil, gercek bir senaryo).
     */
    static final String SYSTEM_USER = "system";

    private final AuditLogRepository auditLogRepository;
    private final UserRoleCacheService userRoleCacheService;
    private final UserRepository userRepository;
    private final Tracer tracer;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRoleCacheService userRoleCacheService,
            UserRepository userRepository,
            Tracer tracer) {
        this.auditLogRepository = auditLogRepository;
        this.userRoleCacheService = userRoleCacheService;
        this.userRepository = userRepository;
        this.tracer = tracer;
    }

    public void record(OperationType operationType, TargetType targetType, Long targetId, String detail) {
        String username = currentUsername();
        Long userId = resolveUserId(username);
        String traceId = currentTraceId();

        auditLogRepository.save(new AuditLog(
                userId, username, operationType, targetType, targetId, detail, traceId));
    }

    /** {@code GET /api/audit-logs} icin — hepsi opsiyonel filtreler, bkz. AuditLogRepository#search. */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(Long userId, TargetType targetType, Instant from, Instant to, Pageable pageable) {
        return auditLogRepository.search(userId, targetType, from, to, pageable);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : SYSTEM_USER;
    }

    /**
     * Id'yi once rol cache'inden okur — bu istek zaten {@code JwtAuthenticationFilter} tarafindan
     * doldurulmus/okunmus oldugu icin pratikte hep cache hit olur, ekstra DB sorgusu gerekmez.
     * Cache miss/erisilemez ihtimaline karsi DB'ye duser. {@link #SYSTEM_USER} icin DB'de
     * hicbir zaman bir satir olmayacagi icin arama yapilmadan direkt null donulur.
     */
    private Long resolveUserId(String username) {
        if (SYSTEM_USER.equals(username)) {
            return null;
        }
        return userRoleCacheService.get(username)
                .map(UserRoleCacheService.CachedUser::id)
                .orElseGet(() -> userRepository.findByUsername(username)
                        .orElseThrow(() -> new NotFoundException(
                                "NOT_FOUND_USER", "user not found: " + username,
                                Map.of("id", username)))
                        .getId());
    }

    /** OTel bu projede her zaman kurulu oldugu icin {@link Tracer} dogrudan enjekte ediliyor (bkz. SpanRunner ile ayni yaklasim); aktif span yoksa null doner. */
    private String currentTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() : null;
    }
}
