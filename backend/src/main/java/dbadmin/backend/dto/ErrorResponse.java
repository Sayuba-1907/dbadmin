package dbadmin.backend.dto;

import java.time.Instant;
//ErrorResponse, projede bir hata çıktığında ön yüzün kafası karışmasın ve
// hep aynı standart formatta hata mesajı okuyabilsin diye tasarlanmış standart hata raporu kâğıdıdır.

// Consistent error body for every 4xx/5xx response, so the frontend always
// has the same shape to read a message off of, regardless of which
// exception type triggered it.
public record ErrorResponse(Instant timestamp, int status, String error, String message) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message);
    }
}
