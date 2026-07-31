package dbadmin.backend.exception;

import java.util.Map;

/**
 * Istenen Tablo/Kolon/Tag id'si yok. {@link GlobalExceptionHandler} bunu HTTP 404 Not Found'a cevirir.
 */
public class NotFoundException extends RuntimeException {

    private final String code;
    private final Map<String, String> details;

    public NotFoundException(String code, String message) {
        this(code, message, Map.of());
    }

    /** {@code code}/{@code details} — bkz. {@link ValidationException}'daki aciklama, ayni mantik. */
    public NotFoundException(String code, String message, Map<String, String> details) {
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
