package dbadmin.backend.security;

import dbadmin.backend.entity.Role;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.service.UserService;
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
 * <p>
 * Not: bu dosyada Spring Security'nin {@code User} sinifi (import edildi) ile bizim
 * {@code dbadmin.backend.entity.User} entity'miz bir arada gecebiliyor — ikinciyi bilerek
 * fully-qualified kullaniyoruz, aksi halde ayni isimde iki sinif kafa karistirirdi.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final UserRoleCacheService userRoleCacheService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserService userService,
            UserRoleCacheService userRoleCacheService) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.userRoleCacheService = userRoleCacheService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            assignIdentity(request, token);
        }
        filterChain.doFilter(request, response);
    }

    /** {@code Authorization: Bearer xxx} basligindan token'i ayirir; baslik yoksa/bicimsizse null. */
    private String extractToken(HttpServletRequest request) {
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
    private void assignIdentity(HttpServletRequest request, String token) {
        try {
            String username = jwtService.extractUsername(token);
            UserDetails userDetails = loadUser(username);

            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException | NotFoundException ex) {
            // Token bozuk/suresi dolmus ya da kullanici bu arada silinmis.
            log.debug("gecersiz JWT, kimlik atanmadi: {}", ex.getMessage());
        }
    }

    /**
     * Once Redis'e bakar; oradaysa DB'ye hic gitmeden {@link UserDetails} kurar. Cache miss'te
     * (ya da Redis erisilemezse — bkz. {@link UserRoleCacheService}) {@link UserService}
     * uzerinden DB'ye gider ve sonucu Redis'e yazar.
     * <p>
     * Bilerek {@link UserDetailsServiceImpl} degil {@link UserService} kullaniliyor:
     * {@code UserDetailsServiceImpl.loadUserByUsername} login sirasinda {@code AuthenticationManager}
     * tarafindan da cagriliyor ve parola hash'ini tasiyor — o metodun icine cache koysaydik parola
     * hash'i Redis'e yazilirdi. Bu yol zaten parolasiz calisiyor (bkz. {@link #assignIdentity}'daki
     * {@code credentials = null}), o yuzden {@code User} burada bos bir parola alaniyla kuruluyor.
     */
    private UserDetails loadUser(String username) {
        return userRoleCacheService.get(username)
                .<UserDetails>map(cached -> newUserDetails(username, cached.role()))
                .orElseGet(() -> {
                    dbadmin.backend.entity.User user = userService.getUserByUsername(username);
                    userRoleCacheService.put(username, user.getId(), user.getRole());
                    return newUserDetails(username, user.getRole());
                });
    }

    private UserDetails newUserDetails(String username, Role role) {
        return new User(username, "", List.of(new SimpleGrantedAuthority(role.authority())));
    }
}
