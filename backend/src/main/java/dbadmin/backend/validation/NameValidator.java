package dbadmin.backend.validation;

import dbadmin.backend.exception.ValidationException;
import java.util.regex.Pattern;

// Shared name rules for both table names and column names, per spec:
// length 2-30, must not start with an uppercase letter, letters/digits/underscore only.
public final class NameValidator {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_][A-Za-z0-9_]{1,29}$");

    private NameValidator() {
    }

    public static void validate(String label, String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new ValidationException(
                    label + " must be 2-30 characters, must not start with an uppercase letter, "
                            + "and may only contain letters, digits and underscore");
        }
    }
}
