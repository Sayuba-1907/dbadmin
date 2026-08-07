package dbadmin.backend.controller;

import dbadmin.backend.dto.AuditLogResponse;
import dbadmin.backend.entity.TargetType;
import dbadmin.backend.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kalici audit kaydinin okuma ucu. Tum uclar <b>sadece ADMIN</b> rolune aciktir — kural
 * {@code SecurityConfig}'te yol bazinda tanimlidir ({@code /api/audit-logs/**}),
 * {@code /api/users/**} ile ayni seviyede.
 */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Log", description = "Kalici audit kaydi (sadece ADMIN)")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Operation(
            summary = "Audit kayitlarini sayfali listeler",
            description = "Tum filtreler opsiyoneldir ve birlikte kullanilabilir. Sayfalama icin "
                    + "standart Spring parametreleri gecerlidir: page, size, sort (ör. "
                    + "sort=createdAt,desc).")
    @GetMapping
    public Page<AuditLogResponse> search(
            @Parameter(description = "Sadece bu kullanicinin islemleri.", example = "1")
                    @RequestParam(required = false) Long userId,
            @Parameter(description = "Sadece bu turden hedeflere yapilan islemler.", example = "TABLE")
                    @RequestParam(required = false) TargetType targetType,
            @Parameter(description = "Bu zamandan sonra (dahil).", example = "2026-08-01T00:00:00Z")
                    @RequestParam(required = false) Instant from,
            @Parameter(description = "Bu zamana kadar (dahil).", example = "2026-08-31T23:59:59Z")
                    @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return auditLogService.search(userId, targetType, from, to, pageable)
                .map(AuditLogResponse::from);
    }
}
