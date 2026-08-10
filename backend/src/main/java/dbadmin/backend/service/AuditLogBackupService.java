package dbadmin.backend.service;

import dbadmin.backend.dto.AuditLogBackupEntryDto;
import dbadmin.backend.dto.AuditLogBackupFile;
import dbadmin.backend.dto.AuditLogBackupListItemDto;
import dbadmin.backend.dto.AuditLogBackupMetaDto;
import dbadmin.backend.dto.AuditLogBackupResponse;
import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.AuditLogRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Audit log yedekleme orkestrasyonu — bkz. requirement-maintenance-audit-backup.md Req-2.4/2.5.
 * <p>
 * Sira kasitli ve degistirilemez (Req-3.2): once MinIO'ya yaz, <b>sadece</b> yukleme basariliysa
 * DB'den sil. MinIO nesne yazimi ile Postgres silme islemi tek bir transaction'da birlesemez
 * (biri object storage, biri DB) — bu yuzden atomicity yerine sira ile guvenlik saglanir:
 * {@link MinioService#upload} hata firlatirsa {@link #backup} da firlatir ve asagidaki silme
 * satirina hic ulasilmaz (Req-3.3, fail-closed, Redis'teki fail-open'in tam tersi).
 */
@Service
public class AuditLogBackupService {

    private static final DateTimeFormatter KEY_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(ZoneOffset.UTC);

    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final MinioService minioService;
    private final ObjectMapper objectMapper;

    public AuditLogBackupService(
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
            MinioService minioService,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.minioService = minioService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogBackupResponse backup() {
        List<AuditLog> rows = auditLogRepository.findAllByOrderByIdAsc();
        if (rows.isEmpty()) {
            throw new ValidationException(
                    "VALIDATION_AUDIT_LOG_BACKUP_EMPTY", "there are no audit log rows to back up");
        }
        // Silme adiminin sinirini (Req-2.5) burada, okuma anindaki en yuksek id'ye sabitliyoruz —
        // asagida MinIO upload surerken (network gecikmesi) yazilacak yeni satirlar bu id'nin
        // ustunde olacagi icin silinmeyecek.
        Long cutoffId = rows.get(rows.size() - 1).getId();

        Instant backedUpAt = Instant.now();
        AuditLogBackupMetaDto meta =
                new AuditLogBackupMetaDto(auditLogService.currentUsername(), backedUpAt, rows.size());
        List<AuditLogBackupEntryDto> entries = rows.stream().map(AuditLogBackupEntryDto::from).toList();
        byte[] content = objectMapper.writeValueAsBytes(new AuditLogBackupFile(meta, entries));
        String key = "backup-" + KEY_TIMESTAMP.format(backedUpAt) + ".json";

        minioService.upload(key, content, "application/json");

        auditLogRepository.deleteByIdLessThanEqual(cutoffId);

        return new AuditLogBackupResponse(key, rows.size(), backedUpAt);
    }

    /**
     * Gecmis yedeklerin listesi (Req-2.6'nin genisletilmesi — kullanicinin talebiyle eklendi,
     * orijinal kapsamda "sadece MinIO console" denmisti, indirme/geri yukleme hala yok, sadece
     * gorunurluk). Her dosya kucuk oldugu icin (satir basina birkac yuz bayt) tek tek indirip
     * meta blogunu okumak burada maliyetli degil.
     */
    public List<AuditLogBackupListItemDto> listBackups() {
        return minioService.listBackupKeys().stream()
                .map(key -> {
                    AuditLogBackupFile file = objectMapper.readValue(minioService.download(key), AuditLogBackupFile.class);
                    AuditLogBackupMetaDto meta = file.meta();
                    return new AuditLogBackupListItemDto(key, meta.backedUpBy(), meta.backedUpAt(), meta.rowCount());
                })
                .sorted(Comparator.comparing(AuditLogBackupListItemDto::backedUpAt).reversed())
                .toList();
    }
}
