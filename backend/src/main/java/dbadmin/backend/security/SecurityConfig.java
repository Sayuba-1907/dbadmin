package dbadmin.backend.security;

import dbadmin.backend.entity.Rol;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Guvenligin tek karar noktasi: hangi ucun kime acik oldugu burada yazar.
 *
 * <h2>Acik birakilan uclar ve neden</h2>
 * <ul>
 *   <li>{@code /api/auth/login} — giris yapmadan token alinamayacagi icin zorunlu olarak acik.
 *   <li>{@code /actuator/prometheus} ve {@code /actuator/health} — <b>Prometheus bu ucu 15
 *       saniyede bir kimlik dogrulamasiz kaziyor</b>. Kapatilirsa Grafana panolari sessizce
 *       boslar (bkz. prometheus.yml). Bu uclar olcum verisi doner, is verisi degil.
 *   <li>Swagger ({@code /swagger-ui/**}, {@code /v3/api-docs/**}) — API dokumantasyonu; kapali
 *       olsaydi hocaya/ekibe API'yi gostermek icin once giris yapmak gerekirdi.
 * </ul>
 *
 * <h2>Yetki kurallari</h2>
 * <ul>
 *   <li>GET {@code /api/**} → giris yapmis <b>herkes</b> (VIEWER dahil) okuyabilir.
 *   <li>Diger metodlar {@code /api/**} → EDITOR veya ADMIN ("kimisi update edebilsin kimisi
 *       edemesin").
 *   <li>{@code /api/kullanicilar/**} → sadece ADMIN. Bu kural digerlerinden <b>once</b>
 *       yazilmalidir: kurallar sirayla degerlendirilir, ilk eslesen kazanir.
 * </ul>
 *
 * <h2>Neden stateless / CSRF kapali</h2>
 * Kimlik her istekte JWT ile tasindigi icin sunucu oturum tutmaz
 * ({@link SessionCreationPolicy#STATELESS}). CSRF saldirisi tarayicinin cerezi otomatik
 * gondermesine dayanir; token'i cerezde degil {@code Authorization} basliginda tasidigimiz
 * ve o basligi tarayici kendiliginden eklemedigi icin CSRF korumasi burada gereksizdir.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestSecurityErrorHandler securityErrorHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestSecurityErrorHandler securityErrorHandler,
            CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityErrorHandler = securityErrorHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // WebMvcConfigurer'daki CORS ayari guvenlik zincirine uygulanmaz — bu yuzden
                // CorsConfigurationSource bean'ini burada acikca bagliyoruz. Baglanmazsa
                // frontend (:3000) preflight (OPTIONS) isteginde 401 alir.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/health/**", "/actuator/health")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Tarayici preflight istegi kimlik tasimaz; reddedilirse asil istek hic atilmaz.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Kullanici yonetimi sadece ADMIN — genel /api/** kuralindan ONCE gelmeli.
                        .requestMatchers("/api/kullanicilar/**").hasRole(Rol.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        .requestMatchers("/api/**").hasAnyRole(Rol.EDITOR.name(), Rol.ADMIN.name())
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                // JWT filtresi, kullanici adi/parola filtresinden once calismali: token'la gelen
                // istek zaten kimlikli oldugu icin form-login mekanizmasina hic ugramamali.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * {@link JwtAuthenticationFilter} bir {@code @Component} <b>ve</b> bir {@code Filter}
     * oldugu icin Spring Boot onu kendiliginden servlet filtre zincirine de kaydeder — yani
     * filtre iki yerde birden bulunur: bir kez guvenlik zincirinde (yukarida acikca
     * ekledigimiz), bir kez de zincirin disinda, <b>her istekte</b> (actuator dahil).
     * <p>
     * Bu bean o otomatik kaydi kapatir. Su an zararsiz (guvenlik zinciri daha once calisiyor
     * ve {@code OncePerRequestFilter} ikinci gecisi atliyor), ama filtre siralamasi degisirse
     * kimlik guvenlik baglami yuklenmeden atanmaya calisilir ve teshisi zor bir hataya donusur.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterOtomatikKaydiKapat(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Giris ucunun ({@code AuthController}) parola dogrulamak icin kullandigi bilesen. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }
}
