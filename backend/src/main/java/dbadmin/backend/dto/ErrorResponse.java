package dbadmin.backend.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Her 4xx/5xx cevabinin ayni sekilde donen govdesi (bkz. {@link dbadmin.backend.exception.GlobalExceptionHandler}) —
 * frontend hangi exception firladigina bakmaksizin hep ayni alanlardan mesaji okuyabilir.
 * {@code message} her zaman Ingilizce (log/Postman icin); {@code code} + {@code details} ise
 * frontend'in i18next ile kendi diline cevirmesi icin (bkz. errors.* ceviri anahtarlari).
 */
public record ErrorResponse(
        Instant timestamp, int status, String error, String message, String code, Map<String, String> details) {

    public static ErrorResponse of(
            int status, String error, String message, String code, Map<String, String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, code, details);
    }
}
