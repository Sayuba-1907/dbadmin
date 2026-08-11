package dbadmin.backend.controller;

import dbadmin.backend.dto.AuditLogBackupListItemDto;
import dbadmin.backend.dto.AuditLogBackupResponse;
import dbadmin.backend.dto.ServiceHealthResponse;
import dbadmin.backend.dto.SystemSummaryResponse;
import dbadmin.backend.service.AuditLogBackupService;
import dbadmin.backend.service.MaintenanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maintenance sayfasinin backend uclari (bkz. requirement-maintenance-audit-backup.md). Tum
 * uclar <b>sadece ADMIN</b> rolune aciktir — kural {@code SecurityConfig}'te yol bazinda
 * tanimlidir ({@code /api/maintenance/**}), {@code /api/users/**} ile ayni seviyede.
 */
@RestController
@RequestMapping("/api/maintenance")
@Tag(name = "Maintenance", description = "Sistem bakimi: audit log yedekleme, sistem ozeti, servis sagligi (sadece ADMIN)")
public class MaintenanceController {

    private final AuditLogBackupService auditLogBackupService;
    private final MaintenanceService maintenanceService;

    public MaintenanceController(AuditLogBackupService auditLogBackupService, MaintenanceService maintenanceService) {
        this.auditLogBackupService = auditLogBackupService;
        this.maintenanceService = maintenanceService;
    }

    @Operation(summary = "Sema/tablo/kolon/kullanici sayilarini doner (Req-2.1)")
    @GetMapping("/summary")
    public SystemSummaryResponse summary() {
        return maintenanceService.systemSummary();
    }

    @Operation(summary = "Postgres/Redis/Tempo/Loki icin basit erisilebilirlik gostergesi (Req-2.2)")
    @GetMapping("/health")
    public ServiceHealthResponse health() {
        return maintenanceService.serviceHealth();
    }

    @Operation(
            summary = "Tum audit log kayitlarini MinIO'ya yedekler ve yedeklenen satirlari siler",
            description = "Once MinIO'ya yazar; yukleme basarisiz olursa hicbir satir silinmez "
                    + "(fail-closed, bkz. Req-2.5/Req-3.3). Yedeklenecek satir yoksa 400 doner.")
    @PostMapping("/audit-logs/backup")
    public AuditLogBackupResponse backupAuditLogs() {
        return auditLogBackupService.backup();
    }

    @Operation(summary = "MinIO'daki gecmis yedeklerin listesini doner (Req-2.6)",
            description = "Gorunurluk icin — gecmis geri yukleme hala yok, sadece listeleme ve tekil dosya indirme.")
    @GetMapping("/audit-logs/backups")
    public List<AuditLogBackupListItemDto> listBackups() {
        return auditLogBackupService.listBackups();
    }

    @Operation(summary = "Tek bir yedek dosyasinin JSON icerigini indirir",
            description = "key, /audit-logs/backups listesindeki 'key' alaniyla birebir ayni olmali "
                    + "(backup-<timestamp>.json formati) — baska bir sey kabul edilmez (400).")
    @GetMapping("/audit-logs/backups/{key}")
    public ResponseEntity<byte[]> downloadBackup(@PathVariable String key) {
        byte[] content = auditLogBackupService.downloadBackup(key);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + key + "\"")
                .body(content);
    }
}
