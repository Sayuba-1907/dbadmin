package dbadmin.backend.config;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.types.Expiration;

/**
 * {@code DefaultRedisCacheWriter} (spring-data-redis'in varsayilan {@code RedisCacheWriter}'i)
 * baglantiyi {@code connectionFactory.getConnection()} ile DOGRUDAN aliyor — {@code RedisTemplate}
 * ise ayni islemi {@code RedisConnectionUtils.getConnection(factory, ...)} uzerinden yapiyor. Bu
 * fark, gozlemlenebilirlik acisindan onemli: sadece {@code RedisConnectionUtils} yolundan giden
 * baglantida Redis komutunun span'i aktif trace'e (o an calisan HTTP istegine) parent'lanıyor.
 * Dogrudan {@code getConnection()} ile gidince Lettuce'in kendi tracing hook'u komutu Tempo'ya
 * yine gonderiyor ama parent'siz, kopuk bir trace olarak (2026-08-06 tarihinde curl+Tempo API ile
 * dogrulandi — {@code tracer.currentSpan()} o an dolu olsa bile span kopuk cikiyordu).
 *
 * <p>Bu sinif, {@code @Cacheable}/{@code @CacheEvict}'in kullandigi {@code RedisCacheWriter}'i
 * {@code RedisTemplate} ile ayni baglanti edinme yolunu kullanacak sekilde yeniden yazar — sadece
 * bu projenin kullandigi kadari (lock'suz, senkron) implemente edildi, spring-data-redis'in tum
 * ozelliklerini (locking cache writer, batch strategy) kapsamiyor; ihtiyac olursa genisletilir.
 */
class TracingAwareRedisCacheWriter implements RedisCacheWriter {

    private final RedisConnectionFactory connectionFactory;
    private final CacheStatisticsCollector statistics;

    TracingAwareRedisCacheWriter(RedisConnectionFactory connectionFactory) {
        this(connectionFactory, CacheStatisticsCollector.none());
    }

    private TracingAwareRedisCacheWriter(
            RedisConnectionFactory connectionFactory, CacheStatisticsCollector statistics) {
        this.connectionFactory = connectionFactory;
        this.statistics = statistics;
    }

    private <T> T withConnection(Function<RedisConnection, T> action) {
        RedisConnection connection = RedisConnectionUtils.getConnection(connectionFactory);
        try {
            return action.apply(connection);
        } finally {
            RedisConnectionUtils.releaseConnection(connection, connectionFactory);
        }
    }

    @Override
    public byte[] get(String name, byte[] key) {
        byte[] value = withConnection(connection -> connection.stringCommands().get(key));
        statistics.incGets(name);
        if (value != null) {
            statistics.incHits(name);
        } else {
            statistics.incMisses(name);
        }
        return value;
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return CompletableFuture.completedFuture(get(name, key, ttl));
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        withConnection(connection -> connection.stringCommands()
                .set(key, value, SetCondition.upsert(), expirationOf(ttl)));
        statistics.incPuts(name);
    }

    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        put(name, key, value, ttl);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return withConnection(connection -> {
            Boolean wasAbsent = connection.stringCommands()
                    .set(key, value, SetCondition.ifAbsent(), expirationOf(ttl));
            statistics.incPuts(name);
            return Boolean.TRUE.equals(wasAbsent) ? null : connection.stringCommands().get(key);
        });
    }

    @Override
    public void evict(String name, byte[] key) {
        withConnection(connection -> connection.keyCommands().del(key));
        statistics.incDeletesBy(name, 1);
    }

    @Override
    public void clear(String name, byte[] pattern) {
        withConnection(connection -> {
            Set<byte[]> matchedKeys = connection.keyCommands().keys(pattern);
            if (matchedKeys != null && !matchedKeys.isEmpty()) {
                connection.keyCommands().del(matchedKeys.toArray(new byte[0][]));
            }
            return null;
        });
    }

    @Override
    public CacheStatistics getCacheStatistics(String name) {
        return statistics.getCacheStatistics(name);
    }

    @Override
    public void clearStatistics(String name) {
        statistics.reset(name);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
        return new TracingAwareRedisCacheWriter(connectionFactory, cacheStatisticsCollector);
    }

    private static Expiration expirationOf(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative()
                ? Expiration.from(ttl)
                : Expiration.persistent();
    }
}
