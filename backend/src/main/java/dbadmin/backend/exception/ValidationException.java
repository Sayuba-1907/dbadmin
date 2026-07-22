package dbadmin.backend.exception;
//400 doğrulama
// Maps to HTTP 400: the request itself is malformed (bad name, bad type, ...).
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
