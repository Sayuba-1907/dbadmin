package dbadmin.backend.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;

class NameValidatorTest {

    @Test
    void acceptsMinimumLength() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "ab"));
    }

    @Test
    void acceptsMaximumLength() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "a".repeat(30)));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "a"));
    }

    @Test
    void rejectsTooLong() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "a".repeat(31)));
    }

    @Test
    void rejectsUppercaseFirstCharacter() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "Ogrenci"));
    }

    @Test
    void acceptsUppercaseAfterFirstCharacter() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "ogrenciX"));
    }

    @Test
    void rejectsSpaces() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "og renci"));
    }

    @Test
    void rejectsSymbols() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "ogrenci-1"));
    }

    @Test
    void acceptsDigitsAndUnderscore() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", "ogrenci_2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "VALIDATION_INVALID_NAME", null));
    }
}
