package dbadmin.backend.security;

import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.entity.Rol;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.service.KullaniciService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Her istekte bir kez calisip {@code Authorization: Bearer <token>} basligini kontrol eder.
 * Token gecerliyse kullaniciyi {@link SecurityContextHolder}'a yerlestirir; boylece istegin
 * geri kalani (controller dahil) "kim istedi" bilgisini gorebilir.
 *
 * <h2>Neden gecersiz token'da hemen hata firlatmiyoruz?</h2>
 * Filtre, token yoksa <b>ya da</b> gecersizse sadece kimlik atamadan zinciri devam ettirir.
 * Kararı sonra {@code FilterSecurityInterceptor} verir: istenen uc herkese aciksa (ör.
 * {@code /api/auth/login}) istek sorunsuz gecer, korunuyorsa kimlik olmadigi icin
 * {@link RestSecurityErrorHandler} devreye girip 401 doner. Bu ayrim onemli — filtre
 * dogrudan 401 firlatsaydi acik uclara gecersiz bir token'la ugramak bile hata verirdi.
 *
 * <p>{@code OncePerRequestFilter}'dan tureme sebebi: forward/include gibi durumlarda ayni
 * istek filtre zincirinden birden fazla gecebilir, bu taban sinif bir kez calismayi garanti eder.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final KullaniciService kullaniciService;
    private final KullaniciRolCacheService kullaniciRolCacheService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            KullaniciService kullaniciService,
            KullaniciRolCacheService kullaniciRolCacheService) {
        this.jwtService = jwtService;
        this.kullaniciService = kullaniciService;
        this.kullaniciRolCacheService = kullaniciRolCacheService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = tokenCikar(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            kimlikAta(request, token);
        }
        filterChain.doFilter(request, response);
    }

    /** {@code Authorization: Bearer xxx} basligindan token'i ayirir; baslik yoksa/bicimsizse null. */
    private String tokenCikar(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        return header.substring(PREFIX.length());
    }

    /**
     * Token gecerliyse kullaniciyi guvenlik baglamina koyar. Gecersiz/suresi dolmus token
     * sessizce yok sayilir (sadece DEBUG log) — istemciye ne oldugunu soylemek saldirgana
     * "imza mi tutmadi, sure mi doldu" bilgisini verirdi; korunan bir uca gidiliyorsa zaten
     * 401 donecek.
     */
    private void kimlikAta(HttpServletRequest request, String token) {
        try {
            String kullaniciAdi = jwtService.kullaniciAdiCikar(token);
            UserDetails kullanici = kullaniciYukle(kullaniciAdi);

            var authentication = new UsernamePasswordAuthenticationToken(
                    kullanici, null, kullanici.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | NotFoundException ex) {
            // Token bozuk/suresi dolmus ya da kullanici bu arada silinmis.
            log.debug("gecersiz JWT, kimlik atanmadi: {}", ex.getMessage());
        }
    }

    /**
     * Once Redis'e bakar; oradaysa DB'ye hic gitmeden {@link UserDetails} kurar. Cache miss'te
     * (ya da Redis erisilemezse — bkz. {@link KullaniciRolCacheService}) {@link KullaniciService}
     * uzerinden DB'ye gider ve sonucu Redis'e yazar.
     * <p>
     * Bilerek {@link KullaniciDetailsService} degil {@link KullaniciService} kullaniliyor:
     * {@code KullaniciDetailsService.loadUserByUsername} login sirasinda {@code AuthenticationManager}
     * tarafindan da cagriliyor ve parola hash'ini tasiyor — o metodun icine cache koysaydik parola
     * hash'i Redis'e yazilirdi. Bu yol zaten parolasiz calisiyor (bkz. {@link #kimlikAta}'daki
     * {@code credentials = null}), o yuzden {@code User} burada bos bir parola alaniyla kuruluyor.
     */
    private UserDetails kullaniciYukle(String kullaniciAdi) {
        return kullaniciRolCacheService.get(kullaniciAdi)
                .<UserDetails>map(onbellekli -> yeniKullaniciDetaylari(kullaniciAdi, onbellekli.rol()))
                .orElseGet(() -> {
                    Kullanici kullanici = kullaniciService.getKullaniciByAd(kullaniciAdi);
                    kullaniciRolCacheService.put(kullaniciAdi, kullanici.getId(), kullanici.getRol());
                    return yeniKullaniciDetaylari(kullaniciAdi, kullanici.getRol());
                });
    }

    private UserDetails yeniKullaniciDetaylari(String kullaniciAdi, Rol rol) {
        return new User(kullaniciAdi, "", List.of(new SimpleGrantedAuthority(rol.authority())));
    }
}
