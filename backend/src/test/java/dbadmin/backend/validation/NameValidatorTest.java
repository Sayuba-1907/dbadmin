package dbadmin.backend.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dbadmin.backend.exception.ValidationException;
import org.junit.jupiter.api.Test;

class NameValidatorTest {

    @Test
    void acceptsMinimumLength() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "ab"));
    }

    @Test
    void acceptsMaximumLength() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "a".repeat(30)));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "a"));
    }

    @Test
    void rejectsTooLong() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "a".repeat(31)));
    }

    @Test
    void rejectsUppercaseFirstCharacter() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "Ogrenci"));
    }

    @Test
    void acceptsUppercaseAfterFirstCharacter() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "ogrenciX"));
    }

    @Test
    void rejectsSpaces() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "og renci"));
    }

    @Test
    void rejectsSymbols() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", "ogrenci-1"));
    }

    @Test
    void acceptsDigitsAndUnderscore() {
        assertDoesNotThrow(() -> NameValidator.validate("name", "ogrenci_2"));
    }

    @Test
    void rejectsNull() {
        assertThrows(ValidationException.class, () -> NameValidator.validate("name", null));
    }
}
