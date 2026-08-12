package dbadmin.backend.validation;

import dbadmin.backend.exception.ValidationException;
import java.util.regex.Pattern;

/**
 * Profildeki e-posta alani icin bicim kontrolu — {@link NameValidator} ile ayni desende
 * (tek metotlu, instance'lanamayan yardimci class). Login/JWT bu alana dayanmaz, sadece
 * iletisim amacli oldugu icin kural kasten gevsek (RFC 5322'nin tam karsiligi degil).
 */
public final class EmailValidator {

    /** basit-ama-yeterli desen: en az bir @ ve @'dan sonra bir nokta, bosluk yok. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private EmailValidator() {
    }

    public static void validate(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException(
                    "VALIDATION_INVALID_EMAIL", "email must be a valid address (e.g. name@example.com)");
        }
    }
}
