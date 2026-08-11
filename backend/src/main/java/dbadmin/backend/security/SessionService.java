package dbadmin.backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * "Aktif Oturumlar / diger cihazlardan cikis" ozelligi icin Redis'te tutulan oturum kaydi.
 * <p>
 * JWT kendi basina <b>stateless</b>tir (bkz. JwtService javadoc'u) — sunucu hangi token'larin
 * "hala gecerli" sayilmasi gerektigini bilmez, imza + sure yeterlidir. Bu, erken cikis
 * yapmayi (ör. "diger cihazlardan cikis yap") imkansiz kilar: dagitilmis bir token suresi
 * dolana kadar geri alinamaz. Bu sinif JWT'nin uzerine INCE bir devlet (state) katmani ekler:
 * her uretilen token'in {@code jti}'si burada kayitlidir, {@link JwtAuthenticationFilter} her
 * istekte token'in imza+sure kontrolunun YANI SIRA burada hala kayitli olup olmadigina bakar.
 * Silinen bir jti, token'in kendi suresi dolmamis olsa bile artik gecersiz sayilir.
 *
 * <h2>Fail-open</h2>
 * {@link UserRoleCacheService} ile ayni gerekce: Redis erisilemezse hatalar yutulur ve
 * "gecerli" varsayilir (ban degil, sadece erken-iptal ozelligi devre disi kalir) — JWT'nin
 * kendi imza/sure kontrolu zaten birincil guvenlik sinirini korur, bu sinif sadece EK bir
 * iptal imkani sunar, Redis'in kendisi auth'un olmazsa olmazi degildir.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String KEY_PREFIX = "session:";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public SessionService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${app.jwt.expiration}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public record SessionInfo(String jti, Instant issuedAt, String userAgent) {
    }

    /** Login'de (ya da kullanici adi degisince taze token uretilirken) cagrilir. */
    public void record(String username, String jti, String userAgent) {
        try {
            String key = key(username);
            String value = Instant.now() + "|" + (userAgent == null ? "" : userAgent);
            redisTemplate.opsForHash().put(key, jti, value);
            // Hash'in TAMAMININ TTL'i her yeni oturumda sifirlanir (Redis'te alan-bazli TTL
            // yok) — bu, cok eski bir oturumun kaydini biraz uzatabilir ama zararsizdir:
            // JwtAuthenticationFilter zaten token'in KENDI exp claim'ini once kontrol eder,
            // bu Redis kaydi sadece EK bir erken-iptal imkanidir, asil sure sinirini JWT belirler.
            redisTemplate.expire(key, ttl);
        } catch (Exception ex) {
            log.warn("oturum redis'e kaydedilemedi, 'diger cihazlardan cikis' bu oturum icin calismayabilir: {}",
                    ex.getMessage());
        }
    }

    /** Token'in exp/imza kontrolunden SONRA cagrilir — bkz. JwtAuthenticationFilter. */
    public boolean exists(String username, String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key(username), jti));
        } catch (Exception ex) {
            log.warn("redis'ten oturum kontrol edilemedi, fail-open (gecerli sayiliyor): {}", ex.getMessage());
            return true;
        }
    }

    public List<SessionInfo> list(String username) {
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key(username));
            List<SessionInfo> sessions = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : fields.entrySet()) {
                sessions.add(parse((String) entry.getKey(), (String) entry.getValue()));
            }
            return sessions;
        } catch (Exception ex) {
            log.warn("redis'ten oturum listesi okunamadi: {}", ex.getMessage());
            return List.of();
        }
    }

    private SessionInfo parse(String jti, String value) {
        int separatorIndex = value.indexOf('|');
        Instant issuedAt = Instant.parse(value.substring(0, separatorIndex));
        String userAgent = value.substring(separatorIndex + 1);
        return new SessionInfo(jti, issuedAt, userAgent.isEmpty() ? null : userAgent);
    }

    /** Tek bir oturumu sonlandirir — jti bu kullanicinin hash'inde yoksa sessizce hicbir sey yapmaz (idempotent). */
    public void revoke(String username, String jti) {
        try {
            redisTemplate.opsForHash().delete(key(username), jti);
        } catch (Exception ex) {
            log.warn("oturum redis'ten silinemedi (TTL sonunda kendiliginden duser): {}", ex.getMessage());
        }
    }

    /** "Diger cihazlardan cikis yap" — su anki oturum haric hepsini siler. */
    public void revokeAllExcept(String username, String currentJti) {
        try {
            String key = key(username);
            List<Object> others = redisTemplate.opsForHash().entries(key).keySet().stream()
                    .filter(jti -> !jti.equals(currentJti))
                    .toList();
            if (!others.isEmpty()) {
                redisTemplate.opsForHash().delete(key, others.toArray());
            }
        } catch (Exception ex) {
            log.warn("diger oturumlar redis'ten silinemedi: {}", ex.getMessage());
        }
    }

    private String key(String username) {
        return KEY_PREFIX + username;
    }
}
