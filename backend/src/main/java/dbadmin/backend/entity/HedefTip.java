package dbadmin.backend.entity;

/** Bir {@link AuditLog} satirinin hangi turden bir varlik hakkinda oldugu. */
public enum HedefTip {
    TABLO,
    KOLON,
    SCHEMA,
    TAG,
    KULLANICI
}
