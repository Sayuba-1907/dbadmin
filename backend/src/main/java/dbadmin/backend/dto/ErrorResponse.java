package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * Her 4xx/5xx cevabinin ayni sekilde donen govdesi (bkz. {@link dbadmin.backend.exception.GlobalExceptionHandler}) —
 * frontend hangi exception firladigina bakmaksizin hep ayni alanlardan mesaji okuyabilir.
 * {@code message} her zaman Ingilizce (log/Postman icin); {@code code} + {@code details} ise
 * frontend'in i18next ile kendi diline cevirmesi icin (bkz. errors.* ceviri anahtarlari).
 */
public record ErrorResponse(
        @Schema(description = "Hatanin olustugu an.", example = "2026-07-28T07:52:04.372Z") Instant timestamp,
        @Schema(description = "HTTP status kodu.", example = "409") int status,
        @Schema(description = "HTTP status'un okunabilir adi.", example = "Conflict") String error,
        @Schema(description = "Insanin okuyacagi Ingilizce hata mesaji (log/Postman icin).",
                        example = "a table named 'ogrenciler' already exists")
                String message,
        @Schema(description = "Frontend'in i18next ceviri sozlugunde arayacagi sabit hata kodu.",
                        example = "CONFLICT_DUPLICATE_TABLE_NAME")
                String code,
        @Schema(description = "Mesajin icindeki degisken kisimlar (frontend kendi dilindeki sablonu "
                        + "bunlarla doldurur). Hataya gore bos olabilir.",
                        example = "{\"name\": \"ogrenciler\"}")
                Map<String, String> details) {

    public static ErrorResponse of(
            int status, String error, String message, String code, Map<String, String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, code, details);
    }
}
