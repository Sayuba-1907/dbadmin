package dbadmin.backend.exception;

import java.util.Map;

/**
 * Istegin kendisi hatali (gecersiz isim, whitelist disi tip, vs). {@link GlobalExceptionHandler}
 * bunu HTTP 400 Bad Request'e cevirir.
 */
public class ValidationException extends RuntimeException {

    private final String code;
    private final Map<String, String> details;

    public ValidationException(String code, String message) {
        this(code, message, Map.of());
    }

    /**
     * {@code code}: frontend'in i18next ceviri sozlugunde arayacagi sabit anahtar (mesela
     * {@code "CONFLICT_DUPLICATE_TABLE_NAME"}) — {@code message} insanin okuyacagi Ingilizce
     * cumle, {@code code} ise makinenin okuyup dile gore ceviri secmesi icin.
     * {@code details}: mesajin icindeki degisken kismi (mesela tablo adi), boylece frontend
     * "'{{name}}' tablosu zaten var" gibi bir sablonu kendi dilinde doldurabilir.
     */
    public ValidationException(String code, String message, Map<String, String> details) {
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
