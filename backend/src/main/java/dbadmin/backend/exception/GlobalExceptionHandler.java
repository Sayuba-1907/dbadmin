package dbadmin.backend.exception;

import dbadmin.backend.dto.ErrorResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Merkezi hata yakalama noktasi. {@code @RestControllerAdvice}, tum controller'larda
 * (try/catch yazmadan) firlatilan bu 3 exception'i tek yerden yakalayip, spec'in istedigi
 * HTTP status koduna ve hep ayni govde sekline ({@link ErrorResponse}) ceviriyor —
 * frontend her hatada ayni formatta JSON bekleyebiliyor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    /** Ayni sekildeki hata govdesini uretmek icin ortak yardimci — 3 handler da bunu cagirir. */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, String code, Map<String, String> details) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, code, details));
    }
}
