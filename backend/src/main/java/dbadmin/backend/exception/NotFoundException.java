package dbadmin.backend.exception;

// Maps to HTTP 404: the referenced Tablo/Kolon/Tag id does not exist.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
