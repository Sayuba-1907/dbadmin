package dbadmin.backend.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS (Cross-Origin Resource Sharing) ayarlari.
 * Tarayici, farkli origin'den (frontend: localhost:3000) atilan istekleri
 * backend (localhost:8081) izin vermedigi surece engeller. Bu config
 * olmadan frontend'in fetch cagrilari tarayicida sessizce reddedilir.
 * <p>
 * Ayar bir {@link CorsConfigurationSource} bean'i olarak veriliyor, eskiden oldugu gibi
 * {@code WebMvcConfigurer.addCorsMappings} ile degil: Spring Security eklendikten sonra
 * istekler once guvenlik filtre zincirinden geciyor ve <b>oradaki CORS isleyicisi MVC'nin
 * ayarini gormuyor</b>. Tek bir bean, her iki tarafin da okudugu ortak kaynak olur
 * (guvenlik tarafina baglanmasi icin bkz. {@code SecurityConfig.filterChain}).
 */
@Configuration
public class WebConfig {

    /** Frontend'in gelistirme sunucusu; uretimde bu listeye gercek alan adi eklenir. */
    private static final List<String> ALLOWED_ORIGINS = List.of("http://localhost:3000");

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(ALLOWED_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        // Frontend token'i "Authorization" basliginda gonderiyor; bu baslik acikca izinli
        // olmazsa tarayici preflight'ta asil istegi hic gondermez.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
