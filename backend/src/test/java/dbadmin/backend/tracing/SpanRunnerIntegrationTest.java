package dbadmin.backend.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dbadmin.backend.AbstractIntegrationTest;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link SpanRunner} (Req-2.2, dual-write span'leri: bkz. {@code plan-otel-implementation.md}
 * Faz 3) icin otomatik regresyon testi — verilen isimle span uretmesi, ic ice cagrildiginda
 * dogru parent'lanmasi ve hata durumunda span'i "error" isaretlemesi. Gercek Tempo container'ina
 * ihtiyac duymadan, gercek OTel SDK + micrometer-tracing-bridge-otel ile uretilen span'leri
 * bellekte ({@link InMemorySpanExporter}) yakalayip dogruluyor.
 *
 * <p>{@code @Cacheable}'in Redis span'leri icin ayni yontemle yazilan regresyon testi
 * {@link dbadmin.backend.controller.CacheableRedisTracingIntegrationTest}'te — o testin gercek
 * bir HTTP istegi (MockMvc) uzerinden gitmesi gerekiyor, cunku bu sinifta oldugu gibi elle
 * {@code tracer.withSpan(...)} ile acilan bir span, servlet/filter-chain baglami disinda
 * Lettuce'in native tracing hook'una dogru parent olarak ulasmiyor (yalnizca gercek bir istek
 * icinde calisirken calisan bir mekanizma — canli dogrulandi).
 */
// management.tracing.export.otlp.enabled=false: OTLP exporter'i kapatiyoruz. Ac
// olsaydi her span.end()'de gercek Tempo'ya (tempo:4318, testte cozulemeyen bir hostname)
// export denenip DNS/retry ile yavaslayacakti -- bu, BatchSpanProcessor'in TEK worker thread'ini
// mesgul ederek sonraki testlerin forceFlush() cagrisini gecikmeye/yarisa (flaky) sokuyordu
// (canli gozlemlendi: testler tek tek calisinca geciyor, birlikte calisinca bazen span
// bulunamiyordu). InMemorySpanExporter zaten testin ihtiyaci olan tek exporter.
@TestPropertySource(properties = "management.tracing.export.otlp.enabled=false")
@Import(InMemorySpanExporterTestConfig.class)
class SpanRunnerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpanRunner spanRunner;

    @Autowired
    private Tracer tracer;

    @Autowired
    private InMemorySpanExporter spanExporter;

    @Autowired
    private SdkTracerProvider sdkTracerProvider;

    @BeforeEach
    void temizle() {
        spanExporter.reset();
    }

    @Test
    void inSpan_verilenIsimleSpanUretir() {
        spanRunner.inSpan("test-span-adi", () -> "sonuc");

        assertTrue(finishedSpanNamed("test-span-adi").isPresent());
    }

    @Test
    void inSpan_icIceCagrildiginda_icSpanDisSpaninCocuguOlur() {
        Span disSpan = tracer.nextSpan().name("manuel-dis-span").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(disSpan)) {
            spanRunner.inSpan("ic-span", () -> null);
        } finally {
            disSpan.end();
        }

        SpanData icSpanData = finishedSpanNamed("ic-span")
                .orElseGet(() -> fail("ic-span bulunamadi, gorulenler: " + spanNames()));
        assertEquals(disSpan.context().spanId(), icSpanData.getParentSpanId());
    }

    @Test
    void inSpan_actionHataFirlatirsa_spanHataIsaretlenirVeHataYukariGider() {
        assertThrows(RuntimeException.class, () -> spanRunner.inSpan("hatali-span", () -> {
            throw new RuntimeException("kasitli test hatasi");
        }));

        SpanData hataliSpanData = finishedSpanNamed("hatali-span")
                .orElseGet(() -> fail("hatali-span bulunamadi"));
        assertEquals(StatusCode.ERROR, hataliSpanData.getStatus().getStatusCode());
    }

    private Optional<SpanData> finishedSpanNamed(String name) {
        sdkTracerProvider.forceFlush().join(5, TimeUnit.SECONDS);
        return spanExporter.getFinishedSpanItems().stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst();
    }

    private List<String> spanNames() {
        return spanExporter.getFinishedSpanItems().stream().map(SpanData::getName).toList();
    }
}
