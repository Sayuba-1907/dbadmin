package dbadmin.backend.scheduler;

import dbadmin.backend.service.ReportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Saat basi otomatik rapor tetikleyicisi — bkz. {@code requirement-scheduler-report.md} Req-2.1.
 * Kendisi {@code @Async} DEGIL: Spring'in scheduler'i zaten kendi ayri thread'inde calisir,
 * bloklanmamasi gereken tek kisim SMTP cagrisi (bkz. {@link ReportService#sendReport}), o yuzden
 * async orada.
 */
@Component
public class ReportScheduler {

    private final ReportService reportService;

    public ReportScheduler(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Cron ifadesi container'in calistigi saat dilimine gore yorumlanir (bkz. Req-3.6). */
    @Scheduled(cron = "0 0 * * * *")
    public void sendHourlyReport() {
        String content = reportService.buildReportContent();
        reportService.sendReport(content);
    }
}
