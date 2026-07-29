package dbadmin.backend.controller;

import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.LoginRequest;
import dbadmin.backend.dto.LoginResponse;
import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.security.JwtService;
import dbadmin.backend.service.KullaniciService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Giris ve "ben kimim" uclari. {@code /api/auth/login} kimlik dogrulamasiz erisilebilen tek API ucudur. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Giris yapma ve token alma")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final KullaniciService kullaniciService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            KullaniciService kullaniciService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.kullaniciService = kullaniciService;
    }

    /**
     * Kullanici adi/parola dogruysa imzali bir JWT doner. Parola karsilastirmasini
     * {@code AuthenticationManager} yapar (BCrypt ile); yanlissa {@code BadCredentialsException}
     * firlatir ve {@link dbadmin.backend.exception.GlobalExceptionHandler} bunu 401'e cevirir.
     */
    @Operation(
            summary = "Giris yapar ve JWT doner",
            description = "Kullanici adi ve parola dogruysa imzali bir token doner. Sonraki isteklerde "
                    + "bu token 'Authorization: Bearer <token>' basliginda gonderilmelidir. "
                    + "Kullanici adinin var olmamasi ile parolanin yanlis olmasi ayni cevabi verir "
                    + "(AUTH_INVALID_CREDENTIALS) — hangi kullanici adlarinin kayitli oldugu "
                    + "denenerek ogrenilemesin diye.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Giris basarili, token dondu"),
        @ApiResponse(
                responseCode = "401",
                description = "Kullanici adi ya da parola hatali (AUTH_INVALID_CREDENTIALS)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.kullaniciAdi(), request.parola()));

        Kullanici kullanici = kullaniciService.getKullaniciByAd(request.kullaniciAdi());
        return new LoginResponse(
                jwtService.tokenUret(kullanici), kullanici.getKullaniciAdi(), kullanici.getRol());
    }

    /**
     * Token'in kime ait oldugunu doner. Frontend'in sayfa yenilendiginde "elimdeki token hala
     * gecerli mi, kimim, rolum ne" sorusunu tek istekte cevaplamasi icin.
     */
    @Operation(
            summary = "Gecerli token'in sahibini doner",
            description = "Elindeki token ile kim oldugunu ogrenmek icin. Token yoksa ya da "
                    + "gecersizse 401 doner, yani ayni zamanda 'token hala gecerli mi' kontroludur.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token gecerli, kullanici bilgisi dondu"),
        @ApiResponse(
                responseCode = "401",
                description = "Token yok, bozuk ya da suresi dolmus (AUTH_REQUIRED)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/ben")
    public ResponseEntity<LoginResponse> ben(Authentication authentication) {
        Kullanici kullanici = kullaniciService.getKullaniciByAd(authentication.getName());
        // Token yeniden uretilmiyor: cagiran zaten gecerli bir token'la geldi, amac sadece
        // kim oldugunu soylemek. Bu yuzden token alani bos.
        return ResponseEntity.ok(
                new LoginResponse(null, kullanici.getKullaniciAdi(), kullanici.getRol()));
    }
}
