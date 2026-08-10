package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/maintenance/summary}'nin donus govdesi (Req-2.1). Sayilar
 * {@code ReportService.buildReportContent()}'teki dort {@code .count()} cagrisiyla aynidir —
 * mantik tekrarlanmiyor, iki servis de ayni repository'lere bagimsizca bagli.
 */
public record SystemSummaryResponse(
        @Schema(description = "Sema sayisi (public haric).", example = "6") long schemaCount,
        @Schema(description = "Tablo sayisi.", example = "12") long tableCount,
        @Schema(description = "Kolon sayisi.", example = "48") long columnCount,
        @Schema(description = "Kullanici sayisi.", example = "5") long userCount) {
}
