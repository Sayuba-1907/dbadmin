package dbadmin.backend.dto;

/**
 * Swagger'da her hata cevabinin altinda gosterilecek gercek ornek govdeler.
 * <p>
 * Neden ayri bir sinif: {@link ErrorResponse} tek bir paylasilan sema oldugu icin, alanlarinin
 * ustundeki tek bir ornek springdoc tarafindan o semayi donen <em>her</em> cevaba aynen
 * basiliyordu — yani "404 Not Found" yazan bir cevabin altinda {@code "status": 409,
 * "code": "CONFLICT_DUPLICATE_TABLE_NAME"} gorunuyordu. Artik her {@code @ApiResponse} kendi
 * ornegini {@code @ExampleObject(value = ErrorExamples.XXX)} ile veriyor; bir ucun ayni HTTP
 * status'u altinda birden fazla kodu varsa hepsi ayri ayri listelenir (Swagger UI bunlari
 * acilir listede gosterir).
 * <p>
 * Buradaki her sabit, {@link dbadmin.backend.exception.GlobalExceptionHandler}'in gercekten
 * urettigi govdenin birebir ayni sekli: {@code timestamp/status/error/message/code/details}.
 * Yeni bir hata kodu eklerken buraya da ornegini eklemek gerekir — kod ile dokumantasyonun
 * ayrisip ayrismadigi tek bakista buradan gorulur.
 */
public final class ErrorExamples {

    private ErrorExamples() {
    }

    // ---------------------------------------------------------------- 404
    public static final String NOT_FOUND_TABLE =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 404,
              "error": "Not Found",
              "message": "tablo not found: 42",
              "code": "NOT_FOUND_TABLE",
              "details": { "id": "42" }
            }""";

    public static final String NOT_FOUND_SCHEMA =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 404,
              "error": "Not Found",
              "message": "schema not found: 7",
              "code": "NOT_FOUND_SCHEMA",
              "details": { "id": "7" }
            }""";

    public static final String NOT_FOUND_COLUMN =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 404,
              "error": "Not Found",
              "message": "column not found in this table: 5",
              "code": "NOT_FOUND_COLUMN",
              "details": { "id": "5" }
            }""";

    public static final String NOT_FOUND_TAG =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 404,
              "error": "Not Found",
              "message": "tag not found: 3",
              "code": "NOT_FOUND_TAG",
              "details": { "id": "3" }
            }""";

    // ---------------------------------------------------------------- 400
    public static final String VALIDATION_INVALID_TABLE_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "table name must be 2-30 characters, must not start with an uppercase letter, and may only contain letters, digits and underscore",
              "code": "VALIDATION_INVALID_TABLE_NAME",
              "details": {}
            }""";

    public static final String VALIDATION_INVALID_COLUMN_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "column name must be 2-30 characters, must not start with an uppercase letter, and may only contain letters, digits and underscore",
              "code": "VALIDATION_INVALID_COLUMN_NAME",
              "details": {}
            }""";

    public static final String VALIDATION_INVALID_SCHEMA_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "schema name must be 2-30 characters, must not start with an uppercase letter, and may only contain letters, digits and underscore",
              "code": "VALIDATION_INVALID_SCHEMA_NAME",
              "details": {}
            }""";

    public static final String VALIDATION_INVALID_TAG_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "tag name must be 2-30 characters, must not start with an uppercase letter, and may only contain letters, digits and underscore",
              "code": "VALIDATION_INVALID_TAG_NAME",
              "details": {}
            }""";

    public static final String VALIDATION_INVALID_COLUMN_TYPE =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "type must be one of: numeric, text, datetime, boolean",
              "code": "VALIDATION_INVALID_COLUMN_TYPE",
              "details": {}
            }""";

    public static final String VALIDATION_TABLE_NEEDS_COLUMN =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "a table needs at least one column",
              "code": "VALIDATION_TABLE_NEEDS_COLUMN",
              "details": {}
            }""";

    public static final String VALIDATION_MISSING_SCHEMA =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "a schema must be specified",
              "code": "VALIDATION_MISSING_SCHEMA",
              "details": {}
            }""";

    public static final String VALIDATION_RESERVED_SCHEMA_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 400,
              "error": "Bad Request",
              "message": "'public' is a reserved schema name and cannot be used",
              "code": "VALIDATION_RESERVED_SCHEMA_NAME",
              "details": { "name": "public" }
            }""";

    // ---------------------------------------------------------------- 409
    public static final String CONFLICT_DUPLICATE_TABLE_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "a table named 'ogrenciler' already exists",
              "code": "CONFLICT_DUPLICATE_TABLE_NAME",
              "details": { "name": "ogrenciler" }
            }""";

    public static final String CONFLICT_DUPLICATE_COLUMN_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "a column named 'yas' already exists in this table",
              "code": "CONFLICT_DUPLICATE_COLUMN_NAME",
              "details": { "name": "yas" }
            }""";

    public static final String CONFLICT_DUPLICATE_COLUMN_IN_REQUEST =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "duplicate column name in request: yas",
              "code": "CONFLICT_DUPLICATE_COLUMN_IN_REQUEST",
              "details": { "name": "yas" }
            }""";

    public static final String CONFLICT_DUPLICATE_SCHEMA_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "a schema named 'ogrenciler' already exists",
              "code": "CONFLICT_DUPLICATE_SCHEMA_NAME",
              "details": { "name": "ogrenciler" }
            }""";

    public static final String CONFLICT_DUPLICATE_TAG_NAME =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "a tag named 'onemli' already exists",
              "code": "CONFLICT_DUPLICATE_TAG_NAME",
              "details": { "name": "onemli" }
            }""";

    /**
     * PK yapilmak istenen kolonda tabloda zaten NULL/tekrar eden deger varsa Postgres
     * {@code ADD CONSTRAINT ... PRIMARY KEY}'i reddeder (bkz. TabloService.changeKolonPrimaryKey).
     */
    public static final String CONFLICT_COLUMN_NOT_UNIQUE =
            """
            {
              "timestamp": "2026-07-29T09:12:31.402Z",
              "status": 409,
              "error": "Conflict",
              "message": "the operation conflicts with the table's current data (e.g. a column being made primary key contains null or duplicate values)",
              "code": "CONFLICT_COLUMN_NOT_UNIQUE",
              "details": {}
            }""";
}
