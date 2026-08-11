package dbadmin.backend.service;

import dbadmin.backend.dto.ServiceHealthResponse;
import dbadmin.backend.dto.SystemSummaryResponse;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.repository.UserRepository;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance sayfasinin "sistem ozeti + servis sagligi" bolumu (Req-2.1/2.2). Audit log
 * yedeklemesinden (bkz. {@link AuditLogBackupService}) bilerek ayri: bu servis hicbir yazma
 * yapmaz, sadece okur.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final SchemaRepository schemaRepository;
    private final TableRepository tableRepository;
    private final ColumnRepository columnRepository;
    private final UserRepository userRepository;
    private final HealthContributorRegistry healthContributorRegistry;
    private final MinioClient minioClient;
    private final String minioBucket;

    public MaintenanceService(
            SchemaRepository schemaRepository,
            TableRepository tableRepository,
            ColumnRepository columnRepository,
            UserRepository userRepository,
            HealthContributorRegistry healthContributorRegistry,
            MinioClient minioClient,
            @Value("${app.minio.bucket}") String minioBucket) {
        this.schemaRepository = schemaRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.userRepository = userRepository;
        this.healthContributorRegistry = healthContributorRegistry;
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
    }

    /** {@code ReportService.buildReportContent()}'teki dort {@code .count()} cagrisinin aynisi. */
    @Transactional(readOnly = true)
    public SystemSummaryResponse systemSummary() {
        return new SystemSummaryResponse(
                schemaRepository.count(), tableRepository.count(), columnRepository.count(), userRepository.count());
    }

    /**
     * Postgres/Redis/Backend: Spring Boot'un otomatik urettigi contributor'lardan okunur (ekstra
     * kod gerekmez) — "backend" icin kullanilan "ping" contributor'i her zaman UP doner, bu
     * response'un kendisi uretilebiliyor olmasi zaten backend'in ayakta oldugunun kaniti. MinIO
     * Spring-managed bir kaynak olmadigi icin bucketExists() ile elle kontrol edilir. Hicbir
     * durumda exception yukari cikmaz — hepsi true/false'a normalize edilir (basit yesil/kirmizi
     * gosterge, Req-2.2).
     */
    public ServiceHealthResponse serviceHealth() {
        return new ServiceHealthResponse(
                isContributorUp("db"), isContributorUp("redis"), isMinioUp(), isContributorUp("ping"));
    }

    private boolean isMinioUp() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
        } catch (Exception ex) {
            log.debug("MinIO erisilemedi: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isContributorUp(String contributorName) {
        try {
            HealthContributor contributor = healthContributorRegistry.getContributor(contributorName);
            if (!(contributor instanceof HealthIndicator indicator)) {
                log.warn("'{}' icin bir HealthIndicator bulunamadi (bulunan: {})", contributorName,
                        contributor == null ? "null" : contributor.getClass());
                return false;
            }
            return Status.UP.equals(indicator.health().getStatus());
        } catch (Exception ex) {
            log.warn("'{}' health contributor'i okunamadi: {}", contributorName, ex.getMessage());
            return false;
        }
    }
}
