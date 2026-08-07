package dbadmin.backend.tracing;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot'un OTel autoconfigurasyonu ({@code spring-boot-micrometer-tracing-opentelemetry})
 * classpath'teki her {@code SpanExporter} bean'ini otomatik toplayip tek bir
 * {@code BatchSpanProcessor}'da birlestiriyor (bkz. {@code OpenTelemetryTracingAutoConfiguration
 * #spanExporters}) — bu yuzden burada bir {@link InMemorySpanExporter} bean'i tanimlamak, gercek
 * OTLP exporter'in yaninda calisir (o da denenir ama testte Tempo erisilemez oldugu icin arka
 * planda sessizce timeout olur) ve testlerin uretilen span'leri gercek bir Tempo container'ina
 * ihtiyac duymadan bellekten okumasini saglar.
 */
@TestConfiguration
public class InMemorySpanExporterTestConfig {

    @Bean
    public InMemorySpanExporter testSpanExporter() {
        return InMemorySpanExporter.create();
    }
}
