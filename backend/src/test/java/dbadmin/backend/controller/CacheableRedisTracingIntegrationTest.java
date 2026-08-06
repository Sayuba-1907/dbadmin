package dbadmin.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.tracing.InMemorySpanExporterTestConfig;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-08-06'da bulunan hatanin regresyon testi: varsayilan {@code DefaultRedisCacheWriter}
 * Redis baglantisini {@code connectionFactory.getConnection()} ile dogrudan aldigi icin,
 * {@code @Cacheable}'in urettigi Redis komutu (SET) aktif HTTP istegi span'inin cocugu olarak
 * degil, parent'siz/kopuk bir trace olarak dusuyordu — curl + Tempo API ile canli dogrulandi.
 * {@code TracingAwareRedisCacheWriter}, baglantiyi {@code RedisConnectionUtils} uzerinden alarak
 * bunu duzeltti (bkz. DECISIONS.md "OpenTelemetry gozlemlenebilirlik").
 *
 * <p>Bu davranis yalnizca gercek bir HTTP istegi icinde (servlet filter chain'in actigi
 * observation/span baglami altinda) gozlemlenebiliyor — {@link
 * dbadmin.backend.tracing.SpanRunnerIntegrationTest}'te oldugu gibi elle {@code
 * tracer.withSpan(...)} ile acilan bir span, Lettuce'in native tracing hook'una parent olarak
 * ulasmiyor (canli denendi, basarisiz oldu). Bu yuzden test MockMvc uzerinden gercek bir
 * {@code GET /api/tags} istegi atarak, gercek Spring MVC + Spring Security filter zincirini
 * calistiriyor.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
@TestPropertySource(properties = "management.tracing.export.otlp.enabled=false")
@Import(InMemorySpanExporterTestConfig.class)
class CacheableRedisTracingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private SdkTracerProvider sdkTracerProvider;

    @BeforeEach
    void tagsCacheiTemizle() {
        cacheManager.getCache("tags").clear();
        spanExporter.reset();
    }

    @Test
    void tagsIstegi_cacheMiss_redisSetKomutuHttpIsteginSpaninCocuguOlur() throws Exception {
        mockMvc.perform(get("/api/tags")).andExpect(status().isOk());

        SpanData httpSpan = finishedSpanNamed("http get /api/tags")
                .orElseGet(() -> fail("HTTP root span'i bulunamadi, gorulenler: " + spanNames()));
        SpanData setSpan = finishedSpanNamed("set")
                .orElseGet(() -> fail("redis 'set' span'i bulunamadi, gorulenler: " + spanNames()));

        assertEquals(httpSpan.getTraceId(), setSpan.getTraceId());
    }

    private Optional<SpanData> finishedSpanNamed(String name) {
        sdkTracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return spanExporter.getFinishedSpanItems().stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst();
    }

    private List<String> spanNames() {
        return spanExporter.getFinishedSpanItems().stream().map(SpanData::getName).toList();
    }
}
