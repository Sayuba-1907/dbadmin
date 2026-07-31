package dbadmin.backend.exception;

import java.util.Map;

/**
 * Istek dogru formatta ama mevcut durumla catisiyor (mesela ayni isimde tablo zaten var).
 * {@link GlobalExceptionHandler} bunu HTTP 409 Conflict'e cevirir.
 */
public class ConflictException extends RuntimeException {

    private final String code;
    private final Map<String, String> details;

    public ConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    /** {@code code}/{@code details} — bkz. {@link ValidationException}'daki aciklama, ayni mantik. */
    public ConflictException(String code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
