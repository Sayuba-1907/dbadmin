package dbadmin.backend.validation;

import dbadmin.backend.exception.ValidationException;
import java.util.regex.Pattern;

/**
 * Hem tablo hem kolon isimleri icin ortak kural (spec'ten): 2-30 karakter, ilk karakter
 * buyuk harf olamaz, sadece harf/rakam/alt cizgi. Tek metotlu bir yardimci class oldugu
 * icin instance'lanamasin diye constructor {@code private} ve class {@code final}.
 */
public final class NameValidator {

    /** {@code ^[a-z0-9_][A-Za-z0-9_]{1,29}$}: ilk karakter kucuk harf/rakam/altcizgi, toplam uzunluk 2-30. */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");

    private NameValidator() {
    }

    /**
     * Kural ihlali varsa {@link dbadmin.backend.exception.ValidationException} firlatir (-> HTTP 400).
     * {@code label} sadece Ingilizce mesajda "table name"/"column name" ayrimini gostermek icin;
     * {@code code} ayni ayrimi frontend'in i18next cevirisi secebilmesi icin makine-okunur halde tasir.
     */
    public static void validate(String label, String code, String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new ValidationException(
                    code,
                    label + " must be 2-30 characters, must not start with an uppercase letter, "
                            + "and may only contain letters, digits and underscore");
        }
    }
}
