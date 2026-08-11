package dbadmin.backend.controller;

import dbadmin.backend.dto.AvatarContent;
import dbadmin.backend.dto.ChangePasswordRequest;
import dbadmin.backend.dto.ErrorExamples;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.LoginRequest;
import dbadmin.backend.dto.LoginResponse;
import dbadmin.backend.dto.UpdateProfileRequest;
import dbadmin.backend.entity.User;
import dbadmin.backend.security.JwtService;
import dbadmin.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Giris ve "ben kimim" uclari. {@code /api/auth/login} kimlik dogrulamasiz erisilebilen tek API ucudur. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Giris yapma ve token alma")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
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
                description = "Kullanici adi ya da parola hatali.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "AUTH_INVALID_CREDENTIALS",
                                summary = "Kullanici adi ya da parola yanlis",
                                value = ErrorExamples.AUTH_INVALID_CREDENTIALS)))
    })
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userService.getUserByUsername(request.username());
        return LoginResponse.of(jwtService.generateToken(user), user);
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
                description = "Token yok, bozuk ya da suresi dolmus.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class),
                        examples = @ExampleObject(name = "AUTH_REQUIRED",
                                summary = "Token yok/gecersiz",
                                value = ErrorExamples.AUTH_REQUIRED)))
    })
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        // Token yeniden uretilmiyor: cagiran zaten gecerli bir token'la geldi, amac sadece
        // kim oldugunu soylemek. Bu yuzden token alani bos.
        return ResponseEntity.ok(LoginResponse.of(null, user));
    }

    @Operation(
            summary = "Kendi profilini gunceller (ad soyad ve/veya kullanici adi)",
            description = "Her iki alan da opsiyonel, null olan degismez. Kullanici adi degisirse "
                    + "JWT'nin subject'i artik gecersiz oldugu icin cevapta TAZE bir token doner — "
                    + "istemci bunu sessizce eskisinin yerine yazmali, logout gerekmez.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profil guncellendi"),
        @ApiResponse(
                responseCode = "400",
                description = "Kullanici adi kurallara uymuyor.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Bu kullanici adi zaten var.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me")
    public LoginResponse updateProfile(
            Authentication authentication, @RequestBody UpdateProfileRequest request) {
        User user = userService.updateProfile(
                authentication.getName(), request.fullName(), request.username());
        // Sadece username gercekten degistiyse taze token uret — fullName-only guncellemede
        // eski token hala tamamen gecerli, gereksiz yere yeni bir token dagitmaya gerek yok.
        boolean usernameChanged = request.username() != null && !request.username().equals(authentication.getName());
        String token = usernameChanged ? jwtService.generateToken(user) : null;
        return LoginResponse.of(token, user);
    }

    @Operation(summary = "Kendi parolasini degistirir", description = "Mevcut parola dogru degilse 401 doner.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Parola degistirildi"),
        @ApiResponse(
                responseCode = "400",
                description = "Yeni parola cok kisa.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Mevcut parola yanlis.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
                authentication.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Profil fotografi yukler", description = "Kabul edilen tipler: image/png, image/jpeg, image/webp. En fazla 2MB.")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LoginResponse uploadAvatar(
            Authentication authentication, @RequestParam("file") MultipartFile file) {
        User user = userService.updateAvatar(authentication.getName(), file);
        return LoginResponse.of(null, user);
    }

    @Operation(summary = "Kendi profil fotografini indirir", description = "Foto yoksa 404 doner.")
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> downloadAvatar(Authentication authentication) {
        User user = userService.getUserByUsername(authentication.getName());
        AvatarContent avatar = userService.getAvatar(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.content());
    }

    @Operation(summary = "Profil fotografini kaldirir", description = "Foto zaten yoksa da 200 doner (idempotent).")
    @DeleteMapping("/me/avatar")
    public LoginResponse removeAvatar(Authentication authentication) {
        User user = userService.removeAvatar(authentication.getName());
        return LoginResponse.of(null, user);
    }
}
