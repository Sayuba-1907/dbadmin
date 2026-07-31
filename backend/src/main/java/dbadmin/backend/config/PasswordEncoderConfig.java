package dbadmin.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code PasswordEncoder} eskiden {@code SecurityConfig} icindeydi. {@code KullaniciService}
 * (parola hash'lemek icin buna ihtiyac duyar) {@code JwtAuthenticationFilter} tarafindan
 * kullanilmaya baslayinca ({@code KullaniciRolCacheService} miss'inde DB'den taze rol okumak
 * icin), su donguyu olusturuyordu: {@code SecurityConfig -> JwtAuthenticationFilter ->
 * KullaniciService -> PasswordEncoder (SecurityConfig'in bean'i) -> SecurityConfig}. Bean'i
 * SecurityConfig'ten bagimsiz bu sinifa tasimak donguyu kirdi.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * Parolalar BCrypt ile hash'lenir: ayni parola her seferinde farkli bir hash uretir (icine
     * rastgele bir "salt" gomulur), bu yuzden hazir hash tablolariyla toplu cozum yapilamaz.
     * Ayrica bilerek yavastir — saniyede milyonlarca deneme yapilmasini zorlastirir.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
