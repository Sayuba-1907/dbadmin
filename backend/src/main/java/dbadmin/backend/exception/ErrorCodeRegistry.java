package dbadmin.backend.exception;

import java.util.Map;

/**
 * {@code code} string'inden (ör. {@code NOT_FOUND_TABLE}) uygulamanin kendi sayisal hata koduna
 * (ör. {@code 40401}) tek merkezi eslesme noktasi. {@link dbadmin.backend.dto.ErrorResponse#of}
 * burayi cagirir, yani yeni bir hata firlatan yer eklendiginde hicbir throw call site'i
 * degismez — sadece asagidaki tabloya bir satir eklenir.
 * <p>
 * Sayi semasi: ilk 3 hane gercek HTTP status'u tasir (40401 -> 404 ailesi), son 1-2 hane ayni
 * HTTP status altindaki durumu ayirt eder (40401 = ilk 404 durumu, 40402 = ikincisi, ...).
 * {@code code} string'i (insan icin okunabilir, i18n anahtari) hicbir yerde kaldirilmadi —
 * bu sayi ona bir alternatif degil, ek bir katman (bkz. DECISIONS.md, 2026-08-03).
 */
public final class ErrorCodeRegistry {

    private ErrorCodeRegistry() {
    }

    private static final Map<String, Integer> NUMBERS = Map.ofEntries(
            // 400
            Map.entry("VALIDATION_INVALID_TABLE_NAME", 40001),
            Map.entry("VALIDATION_INVALID_COLUMN_NAME", 40002),
            Map.entry("VALIDATION_INVALID_SCHEMA_NAME", 40003),
            Map.entry("VALIDATION_INVALID_TAG_NAME", 40004),
            Map.entry("VALIDATION_INVALID_COLUMN_TYPE", 40005),
            Map.entry("VALIDATION_TABLE_NEEDS_COLUMN", 40006),
            Map.entry("VALIDATION_MISSING_SCHEMA", 40007),
            Map.entry("VALIDATION_RESERVED_SCHEMA_NAME", 40008),
            Map.entry("VALIDATION_INVALID_USERNAME", 40009),
            Map.entry("VALIDATION_WEAK_PASSWORD", 40010),
            Map.entry("VALIDATION_MISSING_ROLE", 40011),
            Map.entry("VALIDATION_INVALID_PATH_PARAM", 40012),
            Map.entry("VALIDATION_MALFORMED_BODY", 40013),
            Map.entry("VALIDATION_AUDIT_LOG_BACKUP_EMPTY", 40014),
            Map.entry("VALIDATION_PASSWORD_UNCHANGED", 40015),
            Map.entry("VALIDATION_MISSING_AVATAR", 40016),
            Map.entry("VALIDATION_INVALID_AVATAR_TYPE", 40017),
            Map.entry("VALIDATION_AVATAR_TOO_LARGE", 40018),
            Map.entry("VALIDATION_INVALID_AVATAR", 40019),
            Map.entry("VALIDATION_AUDIT_LOG_BACKUP_KEY", 40020),
            Map.entry("VALIDATION_INVALID_PAGE_SIZE", 40021),
            Map.entry("VALIDATION_INVALID_PAGE", 40022),
            Map.entry("VALIDATION_UNKNOWN_COLUMN", 40023),
            Map.entry("VALIDATION_EMPTY_ROW", 40024),
            Map.entry("VALIDATION_NO_PRIMARY_KEY", 40025),
            Map.entry("VALIDATION_MISSING_PRIMARY_KEY_VALUE", 40026),
            Map.entry("VALIDATION_PRIMARY_KEY_READONLY", 40027),
            Map.entry("VALIDATION_INVALID_EMAIL", 40028),
            // 401
            Map.entry("AUTH_INVALID_CREDENTIALS", 40101),
            Map.entry("AUTH_REQUIRED", 40102),
            // 403
            Map.entry("AUTH_FORBIDDEN", 40301),
            // 404
            Map.entry("NOT_FOUND_TABLE", 40401),
            Map.entry("NOT_FOUND_COLUMN", 40402),
            Map.entry("NOT_FOUND_SCHEMA", 40403),
            Map.entry("NOT_FOUND_TAG", 40404),
            Map.entry("NOT_FOUND_USER", 40405),
            Map.entry("NOT_FOUND_ENDPOINT", 40406),
            Map.entry("NOT_FOUND_NOTIFICATION", 40407),
            Map.entry("NOT_FOUND_AVATAR", 40408),
            Map.entry("NOT_FOUND_ROW", 40409),
            // 405
            Map.entry("VALIDATION_METHOD_NOT_ALLOWED", 40501),
            // 409
            Map.entry("CONFLICT_DUPLICATE_TABLE_NAME", 40901),
            Map.entry("CONFLICT_DUPLICATE_COLUMN_NAME", 40902),
            Map.entry("CONFLICT_DUPLICATE_COLUMN_IN_REQUEST", 40903),
            Map.entry("CONFLICT_DUPLICATE_SCHEMA_NAME", 40904),
            Map.entry("CONFLICT_DUPLICATE_TAG_NAME", 40905),
            Map.entry("CONFLICT_DUPLICATE_USERNAME", 40906),
            Map.entry("CONFLICT_COLUMN_NOT_UNIQUE", 40907),
            Map.entry("CONFLICT_LAST_ADMIN", 40908),
            Map.entry("CONFLICT_DUPLICATE_EMAIL", 40909),
            // 500
            Map.entry("INTERNAL_ERROR", 50001),
            // 502 — disaridaki bir bagimlilik basarisiz oldu (uygulamanin kendi hatasi degil)
            Map.entry("EXTERNAL_BACKUP_UPLOAD_FAILED", 50201));

    /** Kayitli olmayan bir {@code code} icin patlar — yeni bir kod eklenip buraya yazilmasi unutulursa sessizce 0 donmez. */
    public static int numberFor(String code) {
        Integer number = NUMBERS.get(code);
        if (number == null) {
            throw new IllegalStateException("ErrorCodeRegistry: no numeric code registered for '" + code + "'");
        }
        return number;
    }
}
