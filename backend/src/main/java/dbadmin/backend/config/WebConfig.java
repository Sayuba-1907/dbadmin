package dbadmin.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Tarayici, farkli origin'den (frontend: localhost:3000) atilan istekleri
// backend (localhost:8080) izin vermedigi surece engeller (CORS). Bu config
// olmadan frontend'in fetch cagrilari tarayicida sessizce reddedilir.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PATCH", "DELETE");
    }
}
