package dbadmin.backend.security;

import dbadmin.backend.entity.Rol;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * {@code kullanici_adi -> {id, rol}} icin manuel bir cache-aside katmani (Spring'in
 * {@code @Cacheable}'i degil, RedisTemplate'i elle cagiriyoruz).
 *
 * <h2>Neden burasi ve neden sadece id+rol</h2>
 * {@link KullaniciDetailsService#loadUserByUsername} hem login'de (parola karsilastirmasi
 * icin {@code parolaHash} lazim) hem her istekte {@link JwtAuthenticationFilter} tarafindan
 * (JWT zaten kimligi kanitladigi icin parola hic kullanilmiyor, {@code credentials} alani
 * {@code null} geciliyor) cagriliyor. Cache'i o metodun icine degil, sadece ikinci (parolasiz)
 * yol icin buraya koyduk — boylece parola hash'i hicbir zaman Redis'e yazilmiyor ve login
 * (zaten seyrek olan bir islem) hep DB'ye gidiyor.
 *
 * <h2>Evict sorumlulugu cagiran tarafta</h2>
 * Bu sinif kendi basina hicbir zaman gecersiz veri EVICT etmez — rol degisince/kullanici
 * silinince {@link dbadmin.backend.service.KullaniciService} bu sinifin {@link #evict}
 * metodunu cagirmak zorunda. Unutulursa {@link #ttl} veriyi en fazla o kadar bayat tutar
 * (guvenlik agi), asil tutarliligi evict cagrisi saglar.
 *
 * <h2>Redis erisilemezse</h2>
 * Her metod hatalari yutup DB'ye dusecek sekilde (fail-open) yazildi: cache bir optimizasyon,
 * bir bagimlilik degil — Redis container'i dursa bile uygulama (biraz daha yavas) calismaya
 * devam etmeli.
 */
@Service
public class KullaniciRolCacheService {

    private static final Logger log = LoggerFactory.getLogger(KullaniciRolCacheService.class);

    private static final String KEY_PREFIX = "user:role:";
    private static final String ALAN_ID = "id";
    private static final String ALAN_ROL = "rol";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public KullaniciRolCacheService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${app.cache.kullanici-rol.ttl-dakika}") long ttlDakika) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(ttlDakika);
    }

    public record OnbellekliKullanici(Long id, Rol rol) {
    }

    /** Cache miss ya da Redis'e erisilemiyorsa {@code Optional.empty()} doner — cagiran taraf DB'ye duser. */
    public Optional<OnbellekliKullanici> get(String kullaniciAdi) {
        try {
            Map<Object, Object> alanlar = redisTemplate.opsForHash().entries(anahtar(kullaniciAdi));
            if (alanlar.isEmpty()) {
                return Optional.empty();
            }
            Long id = Long.valueOf((String) alanlar.get(ALAN_ID));
            Rol rol = Rol.valueOf((String) alanlar.get(ALAN_ROL));
            return Optional.of(new OnbellekliKullanici(id, rol));
        } catch (Exception ex) {
            log.warn("redis'ten kullanici rolu okunamadi, DB'ye dusuluyor: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(String kullaniciAdi, Long id, Rol rol) {
        try {
            String anahtar = anahtar(kullaniciAdi);
            redisTemplate.opsForHash().putAll(anahtar,
                    Map.of(ALAN_ID, String.valueOf(id), ALAN_ROL, rol.name()));
            redisTemplate.expire(anahtar, ttl);
        } catch (Exception ex) {
            log.warn("kullanici rolu redis'e yazilamadi, cache atlanip devam ediliyor: {}", ex.getMessage());
        }
    }

    /** Rol degisince veya kullanici silinince cagrilmali — bkz. sinif javadoc'u. */
    public void evict(String kullaniciAdi) {
        try {
            redisTemplate.delete(anahtar(kullaniciAdi));
        } catch (Exception ex) {
            log.warn("kullanici rolu redis'ten silinemedi (TTL sonunda kendiliginden duser): {}",
                    ex.getMessage());
        }
    }

    private String anahtar(String kullaniciAdi) {
        return KEY_PREFIX + kullaniciAdi;
    }
}
