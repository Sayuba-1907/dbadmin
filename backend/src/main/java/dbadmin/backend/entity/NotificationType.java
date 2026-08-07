package dbadmin.backend.entity;

/**
 * {@link Notification}'in kapsadigi mutasyonlar — bkz. requirement-websocket-notifications.md Req-2.2.
 * {@link OperationType}'nin bir alt kumesi gibi gorunse de bilerek ayri bir enum: audit log HER
 * mutasyonu (schema/tag/kullanici dahil) kapsarken, Notification sadece "tabloyu etkileyen" ve "bir
 * sahibi olan" mutasyonlari kapsar — SCHEMA_, TAG_, USER_ ile baslayan degerleri burada
 * bulundurmak, onlarin yanlislikla bir Notification'a atanabilecegi bir alan acardi.
 */
public enum NotificationType {
    TABLE_RENAMED,
    TABLE_SCHEMA_CHANGED,
    TABLE_DELETED,
    /** "Kaydet'e basinca hepsi birden gitsin" akisinda (bkz. TableService#applyChanges) tek bir ozet bildirimi. */
    TABLE_UPDATED,

    COLUMN_ADDED,
    COLUMN_DELETED,
    COLUMN_RENAMED,
    COLUMN_PRIMARY_KEY_CHANGED,
    COLUMN_TAG_CHANGED
}
