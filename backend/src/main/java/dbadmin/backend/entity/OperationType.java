package dbadmin.backend.entity;

/**
 * {@link AuditLog}'un kapsadigi mutasyonlar (bkz. {@code requirement-audit-log.md} Req-2.1).
 * Salt-okunur (GET) uclar icin deger yok — bunlar hicbir zaman audit'lenmez.
 */
public enum OperationType {
    TABLE_CREATED,
    TABLE_DELETED,
    TABLE_RENAMED,
    TABLE_SCHEMA_CHANGED,
    /** "Kaydet"le tek seferde birden fazla degisiklik uygulandiginda (bkz. TableService#applyChanges) tek bir ozet satiri. */
    TABLE_UPDATED,

    COLUMN_ADDED,
    COLUMN_DELETED,
    COLUMN_RENAMED,
    COLUMN_PRIMARY_KEY_CHANGED,
    COLUMN_TAG_CHANGED,

    SCHEMA_CREATED,
    SCHEMA_RENAMED,
    SCHEMA_DELETED,

    TAG_CREATED,
    TAG_RENAMED,
    TAG_DELETED,

    USER_CREATED,
    USER_ROLE_CHANGED,
    USER_DELETED
}
