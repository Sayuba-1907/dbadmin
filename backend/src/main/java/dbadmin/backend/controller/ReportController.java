package dbadmin.backend.controller;

import dbadmin.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saatlik rapor mailinin manuel tetikleme ucu (Req-2.4) — saat basini beklemeden "simdi rapor
 * istiyorum" ihtiyaci icin. Tum uclar <b>sadece ADMIN</b> rolune aciktir — kural
 * {@code SecurityConfig}'te yol bazinda tanimlidir ({@code /api/reports/**}).
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Raporlar", description = "Saatlik rapor mailinin manuel tetiklenmesi (sadece ADMIN)")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(
            summary = "Rapor mailini simdi gonderir",
            description = "Mail gonderimi asenkron oldugu icin istek aninda biter (202); mailin "
                    + "gercekten gonderilip gonderilmedigi senkron bilinmez, hata olursa sunucu "
                    + "loglarina ERROR seviyesinde dusor (fail-open).")
    @ApiResponses(@ApiResponse(responseCode = "202", description = "Rapor olusturuldu, gonderim baslatildi"))
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send() {
        String content = reportService.buildReportContent();
        reportService.sendReport(content);
    }
}
