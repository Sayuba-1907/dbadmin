package dbadmin.backend.scheduler;

import dbadmin.backend.service.RaporService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Saat basi otomatik rapor tetikleyicisi — bkz. {@code requirement-scheduler-report.md} Req-2.1.
 * Kendisi {@code @Async} DEGIL: Spring'in scheduler'i zaten kendi ayri thread'inde calisir,
 * bloklanmamasi gereken tek kisim SMTP cagrisi (bkz. {@link RaporService#raporGonder}), o yuzden
 * async orada.
 */
@Component
public class RaporScheduler {

    private final RaporService raporService;

    public RaporScheduler(RaporService raporService) {
        this.raporService = raporService;
    }

    /** Cron ifadesi container'in calistigi saat dilimine gore yorumlanir (bkz. Req-3.6). */
    @Scheduled(cron = "0 0 * * * *")
    public void saatlikRaporGonder() {
        String icerik = raporService.raporIcerigiOlustur();
        raporService.raporGonder(icerik);
    }
}
