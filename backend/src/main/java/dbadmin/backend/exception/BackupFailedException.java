package dbadmin.backend.exception;

/**
 * MinIO'ya yedek yukleme basarisiz oldu (bkz. requirement-maintenance-audit-backup.md Req-3.3,
 * fail-closed). {@link GlobalExceptionHandler} bunu HTTP 502 Bad Gateway'e cevirir — hata
 * uygulamanin kendi mantigindan degil, disaridaki bir bagimliliktan geliyor.
 * <p>
 * Bilerek try-catch ile yutulmaz: {@link dbadmin.backend.service.AuditLogBackupService#backup}
 * bu exception'i gorunce DB silme adimina hic gecmez.
 */
public class BackupFailedException extends RuntimeException {

    private static final String CODE = "EXTERNAL_BACKUP_UPLOAD_FAILED";

    public BackupFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() {
        return CODE;
    }
}
