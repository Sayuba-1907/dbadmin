package dbadmin.backend.service;

import dbadmin.backend.dto.ServiceHealthResponse;
import dbadmin.backend.dto.SystemSummaryResponse;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.repository.UserRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Maintenance sayfasinin "sistem ozeti + servis sagligi" bolumu (Req-2.1/2.2). Audit log
 * yedeklemesinden (bkz. {@link AuditLogBackupService}) bilerek ayri: bu servis hicbir yazma
 * yapmaz, sadece okur.
 */
@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    /** MinIO/DB'ye yazma yapan islemlerden farkli olarak burasi kritik degil — 2sn'de cevap gelmezse DOWN say, sayfayi bekletme. */
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final SchemaRepository schemaRepository;
    private final TableRepository tableRepository;
    private final ColumnRepository columnRepository;
    private final UserRepository userRepository;
    private final HealthContributorRegistry healthContributorRegistry;
    private final RestClient restClient;
    private final String tempoReadyUrl;
    private final String lokiReadyUrl;

    public MaintenanceService(
            SchemaRepository schemaRepository,
            TableRepository tableRepository,
            ColumnRepository columnRepository,
            UserRepository userRepository,
            HealthContributorRegistry healthContributorRegistry,
            @Value("${app.maintenance.tempo-ready-url}") String tempoReadyUrl,
            @Value("${app.maintenance.loki-ready-url}") String lokiReadyUrl) {
        this.schemaRepository = schemaRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.userRepository = userRepository;
        this.healthContributorRegistry = healthContributorRegistry;
        this.tempoReadyUrl = tempoReadyUrl;
        this.lokiReadyUrl = lokiReadyUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(HEALTH_CHECK_TIMEOUT);
        requestFactory.setReadTimeout(HEALTH_CHECK_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** {@code ReportService.buildReportContent()}'teki dort {@code .count()} cagrisinin aynisi. */
    @Transactional(readOnly = true)
    public SystemSummaryResponse systemSummary() {
        return new SystemSummaryResponse(
                schemaRepository.count(), tableRepository.count(), columnRepository.count(), userRepository.count());
    }

    /**
     * Postgres/Redis: Spring Boot'un otomatik urettigi contributor'lardan okunur (ekstra kod
     * gerekmez). Tempo/Loki: Spring-managed bir kaynak olmadiklari icin {@code /ready} uclarina
     * elle GET atilir. Hicbir durumda exception yukari cikmaz — hepsi true/false'a normalize
     * edilir (basit yesil/kirmizi gosterge, Req-2.2).
     */
    public ServiceHealthResponse serviceHealth() {
        return new ServiceHealthResponse(
                isContributorUp("db"), isContributorUp("redis"), isReady(tempoReadyUrl), isReady(lokiReadyUrl));
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

    private boolean isReady(String url) {
        try {
            restClient.get().uri(url).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.debug("'{}' erisilemedi: {}", url, ex.getMessage());
            return false;
        }
    }
}
