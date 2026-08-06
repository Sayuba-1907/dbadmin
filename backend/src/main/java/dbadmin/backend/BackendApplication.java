package dbadmin.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Uygulamanin giris noktasi (entry point).
 * {@code @SpringBootApplication} tek basina uc anotasyonu birlestirir:
 * component scan (bu paket altindaki @Controller/@Service/@Repository'leri bulur),
 * auto-configuration (classpath'e gore Spring'in kendi kendini yapilandirmasi)
 * ve @Configuration desteği. {@code main} calisinca embedded Tomcat ayaga kalkar.
 * <p>
 * {@code @EnableScheduling}: {@code RaporScheduler}'daki {@code @Scheduled} metodunun
 * calismasi icin gerekli — yoksa anotasyon sessizce yok sayilir, hicbir hata vermez.
 */
@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
