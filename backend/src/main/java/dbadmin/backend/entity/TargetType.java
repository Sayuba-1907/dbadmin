package dbadmin.backend.entity;

/** Bir {@link AuditLog} satirinin hangi turden bir varlik hakkinda oldugu. */
public enum TargetType {
    TABLE,
    COLUMN,
    SCHEMA,
    TAG,
    USER
}
