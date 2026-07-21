package dbadmin.backend.exception;

// Maps to HTTP 409: the request is well-formed but collides with existing state (duplicate name).
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
