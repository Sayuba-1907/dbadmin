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
        @Schema(
                        description = "Uygulamanin kendi hata kodu — HTTP status'tan ayri ve ondan daha "
                                + "ayrintilidir. Ayni HTTP status'un altinda birden fazla durum olabilir "
                                + "(ör. 404 hem NOT_FOUND_TABLE hem NOT_FOUND_COLUMN olabilir), o yuzden "
                                + "frontend karari status'a degil bu koda bakarak verir ve i18next ceviri "
                                + "sozlugunde bu anahtari arar. Her ucun hangi kodlari dondurebilecegi o "
                                + "ucun cevap orneklerinde ayri ayri listelenmistir.",
                        allowableValues = {
                            "VALIDATION_INVALID_TABLE_NAME",
                            "VALIDATION_INVALID_COLUMN_NAME",
                            "VALIDATION_INVALID_SCHEMA_NAME",
                            "VALIDATION_INVALID_TAG_NAME",
                            "VALIDATION_INVALID_COLUMN_TYPE",
                            "VALIDATION_TABLE_NEEDS_COLUMN",
                            "VALIDATION_MISSING_SCHEMA",
                            "VALIDATION_RESERVED_SCHEMA_NAME",
                            "NOT_FOUND_TABLE",
                            "NOT_FOUND_COLUMN",
                            "NOT_FOUND_SCHEMA",
                            "NOT_FOUND_TAG",
                            "CONFLICT_DUPLICATE_TABLE_NAME",
                            "CONFLICT_DUPLICATE_COLUMN_NAME",
                            "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
                            "CONFLICT_DUPLICATE_SCHEMA_NAME",
                            "CONFLICT_DUPLICATE_TAG_NAME",
                            "CONFLICT_COLUMN_NOT_UNIQUE",
                            "VALIDATION_INVALID_PATH_PARAM",
                            "VALIDATION_MALFORMED_BODY",
                            "VALIDATION_METHOD_NOT_ALLOWED",
                            "NOT_FOUND_ENDPOINT",
                            "INTERNAL_ERROR",
                            "AUTH_INVALID_CREDENTIALS",
                            "AUTH_REQUIRED",
                            "AUTH_FORBIDDEN",
                            "VALIDATION_INVALID_USERNAME",
                            "VALIDATION_WEAK_PASSWORD",
                            "VALIDATION_MISSING_ROLE",
                            "NOT_FOUND_USER",
                            "CONFLICT_DUPLICATE_USERNAME",
                            "CONFLICT_LAST_ADMIN"
                        },
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
