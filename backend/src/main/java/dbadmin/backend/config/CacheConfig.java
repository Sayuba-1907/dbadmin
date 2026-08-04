package dbadmin.backend.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Ana sayfadaki schema/tag/kullanici listeleri icin {@code @Cacheable}/{@code @CacheEvict}
 * altyapisi — {@link dbadmin.backend.security.KullaniciRolCacheService}'in aksine burada
 * Spring'in kendi annotasyon tabanli mekanizmasi kullanildi (notlar'daki "neden @Cacheable
 * kullanilmadi" sorusuna karsilik bilerek: rol cache'i JWT filter'inda parolasiz/ozel bir yolda
 * calisiyordu, bu ucu ise sadece "listele" controller metodlarindan cagriliyor — annotasyonun
 * gizledigi get/put/evict mekanigi burada bir sorun yaratmiyor).
 *
 * <h2>Neden {@code @EnableCaching(order = 0)}</h2>
 * Write metodlari hem {@code @Transactional} hem {@code @CacheEvict} tasiyor (ör.
 * {@code SchemaService.createSchema}). Spring bu iki farkli aspect'i (cache + transaction) proxy
 * zincirine ekliyor ama HANGISININ disarida/HANGISININ icerde saracagi, ikisi de varsayilan
 * order'da ({@code Ordered.LOWEST_PRECEDENCE}) esit oldugu icin BELIRSIZ. Bu onemli, cunku:
 * <ul>
 *   <li>Cache DISARIDA (transaction'i sarmalarsa) -&gt; evict, commit'ten SONRA calisir. Dogru olan bu.</li>
 *   <li>Cache ICERIDE kalirsa -&gt; evict, commit'ten ONCE calisir; commit gerceklesmeden once
 *       gelen bir okuma DB'den (henuz eski) veriyi tekrar cache'e yazabilir, degisiklik commit
 *       olduktan sonra bile o "yeniden dolan" eski deger TTL (15dk) sonuna kadar kalici olurdu.</li>
 * </ul>
 * {@code order = 0}, transaction'in varsayilan (cok daha buyuk) order degerinden dusuk oldugu
 * icin cache advisor'i her zaman disarida birakir — ekstra bir {@code @EnableTransactionManagement}
 * tanimlamaya gerek kalmadan sirayi garantiler.
 */
@Configuration
@EnableCaching(order = 0)
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final RedisConnectionFactory connectionFactory;
    private final Duration ttl;

    public CacheConfig(
            RedisConnectionFactory connectionFactory,
            @Value("${app.cache.workspace.ttl-dakika}") long ttlDakika) {
        this.connectionFactory = connectionFactory;
        this.ttl = Duration.ofMinutes(ttlDakika);
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                // Deger tarafinda JSON: rol cache'inin aksine burada List<Tag>/List<Kullanici>/
                // List<SchemaResponseDTO> gibi karmasik nesneler saklaniyor, duz string yetmez.
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Fail-open: KullaniciRolCacheService'teki ayni felsefe (cache bir optimizasyon, bir
     * bagimlilik degil) — Redis'e ulasilamazsa @Cacheable/@CacheEvict hata firlatip istegi
     * 500'e dusurmek yerine loglayip DB yoluna devam etmeli. Bu handler olmadan varsayilan
     * davranis TAM TERSI: Redis hatasi oldugu gibi caller'a firlar.
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, org.springframework.cache.Cache cache, Object key) {
                log.warn("redis cache okunamadi ({}), DB'ye dusuluyor: {}", cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCachePutError(
                    RuntimeException ex, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("redis cache'e yazilamadi ({}), cache atlanip devam ediliyor: {}",
                        cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, org.springframework.cache.Cache cache, Object key) {
                log.warn("redis cache silinemedi ({}, TTL sonunda kendiliginden duser): {}",
                        cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, org.springframework.cache.Cache cache) {
                log.warn("redis cache temizlenemedi ({}): {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
