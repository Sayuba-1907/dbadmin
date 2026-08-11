package dbadmin.backend.security;

import dbadmin.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * JWT (JSON Web Token) uretir ve dogrular — kimlik dogrulamanin tek "sir" bilen yeri.
 *
 * <h2>JWT nedir, neden oturum yerine bu?</h2>
 * Token uc parcadan olusur: {@code header.payload.signature}. Header ve payload sadece
 * base64'tur, yani <b>herkes okuyabilir</b> — token'a gizli bilgi konmaz. Guvenligi
 * saglayan ucuncu parca: sunucunun gizli anahtariyla uretilen imza. Payload'daki tek bir
 * karakter degistirilse imza tutmaz ve token reddedilir.
 * <p>
 * Kazanci sunucunun <b>durum tutmamasi</b>: klasik oturumda sunucu "hangi oturum kime ait"
 * listesini bellekte tutar, JWT'de ise bu bilgi token'in kendi icindedir. Bedeli de sudur:
 * dagitilmis bir token suresi dolana kadar geri alinamaz, cunku sunucu tarafinda iptal
 * edilecek bir kayit yoktur.
 *
 * <h2>Gizli anahtar</h2>
 * {@code app.jwt.secret} ile gelir ve en az 256 bit (32 bayt) olmalidir — jjwt daha kisasini
 * kabul etmez, uygulama acilista {@code WeakKeyException} ile durur (sessizce guvensiz
 * calismaz). Algoritmayi biz secmiyoruz: {@code signWith(key)} anahtarin uzunluguna gore en
 * guclu HMAC varyantini secer (32 bayt icin HS256, 48 bayt icin HS384, 64 bayt icin HS512),
 * yani daha uzun bir sir vermek dogrudan daha guclu bir imza demektir.
 * <p>
 * Uretimde ortam degiskeninden verilmelidir; varsayilan deger sadece yerel gelistirme icindir.
 */
@Service
public class JwtService {

    /** Rolun payload'da tutuldugu alan. Kullanici adi ayrica JWT'nin standart "subject" alanindadir. */
    private static final String CLAIM_ROLE = "rol";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") Duration expiration) {
        // Keys.hmacShaKeyFor 32 bayttan kisa bir sir verilirse WeakKeyException firlatir,
        // yani zayif bir anahtarla uygulama hic acilmaz (sessizce guvensiz calismaz).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** Token ve onun benzersiz {@code jti}'si (Aktif Oturumlar ozelligi icin, bkz. SessionService). */
    public record IssuedToken(String token, String jti) {
    }

    /**
     * Girisi basarili olan kullanici icin token uretir. Rolu de payload'a koyariz ki her
     * istekte yetki icin veritabanina gitmeye gerek kalmasin — bunun bedeli, rolu degistirilen
     * bir kullanicinin elindeki eski token'in suresi dolana kadar eski rolle gecerli olmasidir.
     * <p>
     * Standart {@code jti} (JWT ID) claim'i her token'a benzersiz bir kimlik verir — token'in
     * kendisi hicbir zaman degistirilemeyecegi (imza tutmaz) icin bu, "hangi token" sorusuna
     * sunucunun disaridan (ör. Redis'te) tutabilecegi tek stabil referanstir. Bkz. SessionService:
     * her jti, "Aktif Oturumlar" ekraninda bir satir, "diger cihazlardan cikis" bunlari siler.
     */
    public IssuedToken generateToken(User user) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(user.getUsername())
                .id(jti)
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti);
    }

    /**
     * Token'i dogrular ve icindeki kullanici adini doner. Imza tutmuyorsa, suresi dolmussa
     * ya da bicimi bozuksa {@link JwtException} firlatir — cagiran taraf
     * ({@link JwtAuthenticationFilter}) bunu 401'e cevirir.
     */
    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    /** Token'in {@code jti}'sini doner — SessionService'te "bu spesifik oturum" anahtari olarak kullanilir. */
    public String extractJti(String token) {
        return claims(token).getId();
    }

    /** Token gecerli mi — sadece evet/hayir isteyen yerler icin (istisna firlatmaz). */
    public boolean isValid(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Imzayi dogrulayip payload'i acar. {@code verifyWith} olmadan {@code parseSignedClaims}
     * cagrilamaz — yani imza dogrulamasini atlamak kutuphane tarafindan engellenir.
     */
    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
