package dbadmin.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dbadmin.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * {@link TracingAwareRedisCacheWriter}, {@code DefaultRedisCacheWriter}'in yerine gecti (bkz.
 * DECISIONS.md "OpenTelemetry gozlemlenebilirlik") — baglanti edinme yolu degisti ama
 * {@code @Cacheable}/{@code @CacheEvict}'in beklentisi olan temel get/put/evict davranisi ayni
 * kalmali. Bu test, gercek Redis'e (Testcontainers) karsi o davranisin bozulmadigini dogrular;
 * span'in trace'e dogru parent'landigi ise koddan degil, calisan sistemden (Grafana Tempo)
 * dogrulanabilir bir sey oldugu icin burada test edilmiyor.
 */
class TracingAwareRedisCacheWriterIntegrationTest extends AbstractIntegrationTest {

    private static final String CACHE_ADI = "tracing-writer-test";

    @Autowired
    private CacheManager cacheManager;

    private Cache cache() {
        Cache cache = cacheManager.getCache(CACHE_ADI);
        cache.clear();
        return cache;
    }

    @Test
    void put_sonra_get_ayniDegeriDoner() {
        Cache cache = cache();

        cache.put("anahtar-1", "deger-1");

        assertEquals("deger-1", cache.get("anahtar-1", String.class));
    }

    @Test
    void get_olmayanAnahtar_nullDoner() {
        Cache cache = cache();

        assertNull(cache.get("hic-yazilmamis-anahtar"));
    }

    @Test
    void evict_sonra_get_nullDoner() {
        Cache cache = cache();
        cache.put("anahtar-2", "deger-2");

        cache.evict("anahtar-2");

        assertNull(cache.get("anahtar-2"));
    }

    @Test
    void put_ayniAnahtaraIkinciYazma_degeriGuncelller() {
        Cache cache = cache();
        cache.put("anahtar-3", "ilk-deger");

        cache.put("anahtar-3", "guncel-deger");

        assertEquals("guncel-deger", cache.get("anahtar-3", String.class));
    }

    @Test
    void clear_tumAnahtarlariSiler() {
        Cache cache = cache();
        cache.put("anahtar-4", "deger-4");
        cache.put("anahtar-5", "deger-5");

        cache.clear();

        assertNull(cache.get("anahtar-4"));
        assertNull(cache.get("anahtar-5"));
    }
}
