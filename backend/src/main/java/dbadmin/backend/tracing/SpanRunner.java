package dbadmin.backend.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Otomatik JDBC enstrumentasyonu metadata (Hibernate) yazma adimini ve gercek DDL calistirma
 * adimini ayni tur span olarak gosterir, ikisi trace'te ayirt edilemez (bkz. CLAUDE.md'deki
 * dual-write notu). Bu sinif, servis katmanindaki bu iki adimi elle isimlendirilmis span'lere
 * sarmak icin kullanilir — Java agent yerine (bkz. DECISIONS.md "mekanizma gorunur olsun")
 * dogrudan {@link Tracer} cagrisi.
 */
@Component
public class SpanRunner {

    private final Tracer tracer;

    public SpanRunner(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> T inSpan(String spanName, Supplier<T> action) {
        return inSpan(spanName, null, null, action);
    }

    public void inSpan(String spanName, Runnable action) {
        inSpan(spanName, () -> {
            action.run();
            return null;
        });
    }

    /** tagKey/tagValue ile span'e ek nitelik eklenir (ör. db.table.name) — asla ham SQL/parametre degeri degil, NameValidator'dan gecmis isim (bkz. Req-3.5). */
    public <T> T inSpan(String spanName, String tagKey, String tagValue, Supplier<T> action) {
        Span span = tracer.nextSpan().name(spanName).start();
        if (tagKey != null) {
            span.tag(tagKey, tagValue);
        }
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return action.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public void inSpan(String spanName, String tagKey, String tagValue, Runnable action) {
        inSpan(spanName, tagKey, tagValue, () -> {
            action.run();
            return null;
        });
    }
}
