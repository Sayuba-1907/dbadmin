package dbadmin.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Uygulamanin giris noktasi (entry point).
 * {@code @SpringBootApplication} tek basina uc anotasyonu birlestirir:
 * component scan (bu paket altindaki @Controller/@Service/@Repository'leri bulur),
 * auto-configuration (classpath'e gore Spring'in kendi kendini yapilandirmasi)
 * ve @Configuration desteği. {@code main} calisinca embedded Tomcat ayaga kalkar.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
