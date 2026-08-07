package dbadmin.backend.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@code @Async} icin ozel bir {@link Executor} tanimlar — Spring, elle bir {@code TaskExecutor}
 * bean'i verilmezse varsayilan olarak {@code SimpleAsyncTaskExecutor} kullanir; bu havuzlama
 * yapmaz, her cagrida yeni bir thread acar. Saatte bir tetiklenen bir is icin pratikte zararsiz
 * olsa da, "varsayilani sorgulamadan kullanma" ilkesi Redis'teki 60sn timeout gotcha'siyla ayni
 * gerekceye dayaniyor (bkz. DECISIONS.md) — bilerek kucuk, sabit boyutlu bir havuz tanimlandi.
 * <p>
 * Bean'e isim verildi ({@code reportTaskExecutor}) cunku birden fazla {@code TaskExecutor} bean'i
 * varsa Spring hangisini kullanacagini bilemez; {@code @Async("reportTaskExecutor")} ile acikca
 * belirtiliyor (bkz. ReportService).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reportTaskExecutor")
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("report-mail-");
        executor.initialize();
        return executor;
    }
}
