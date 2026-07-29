package dbadmin.backend.controller;

import dbadmin.backend.dto.ChangeRolRequest;
import dbadmin.backend.dto.CreateKullaniciRequest;
import dbadmin.backend.dto.ErrorResponse;
import dbadmin.backend.dto.KullaniciResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import dbadmin.backend.service.KullaniciService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanici yonetimi. Tum uclar <b>sadece ADMIN</b> rolune aciktir — kural tek tek metodlarda
 * degil, {@code SecurityConfig}'te yol bazinda tanimlidir ({@code /api/kullanicilar/**}).
 */
@RestController
@RequestMapping("/api/kullanicilar")
@Tag(name = "Kullanicilar", description = "Kullanici yonetimi (sadece ADMIN)")
public class KullaniciController {

    private final KullaniciService kullaniciService;

    public KullaniciController(KullaniciService kullaniciService) {
        this.kullaniciService = kullaniciService;
    }

    @Operation(
            summary = "Tum kullanicilari listeler",
            description = "Parola hash'leri cevaba dahil edilmez.")
    @GetMapping
    public List<KullaniciResponse> list() {
        return kullaniciService.listKullanicilar().stream().map(KullaniciResponse::from).toList();
    }

    @Operation(
            summary = "Yeni kullanici olusturur",
            description = "Parola en az 8 karakter olmali ve sunucuda BCrypt ile hash'lenir. "
                    + "Rol verilmezse VIEWER atanir — yeni kullanici kazara yazma yetkisi almasin diye.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Kullanici olusturuldu"),
        @ApiResponse(
                responseCode = "400",
                description = "Kullanici adi kurallara uymuyor (VALIDATION_INVALID_USERNAME) ya da "
                        + "parola cok kisa (VALIDATION_WEAK_PASSWORD)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Bu kullanici adi zaten var (CONFLICT_DUPLICATE_USERNAME)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KullaniciResponse create(@RequestBody CreateKullaniciRequest request) {
        return KullaniciResponse.from(
                kullaniciService.createKullanici(request.kullaniciAdi(), request.parola(), request.rol()));
    }

    @Operation(
            summary = "Bir kullanicinin rolunu degistirir",
            description = "Sistemdeki son ADMIN'in rolu dusurulemez (CONFLICT_LAST_ADMIN) — aksi halde "
                    + "kullanici yonetimine bir daha girilemezdi.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rol degistirildi"),
        @ApiResponse(
                responseCode = "404",
                description = "Kullanici bulunamadi (NOT_FOUND_USER)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Son ADMIN'in rolu dusurulemez (CONFLICT_LAST_ADMIN)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/rol")
    public KullaniciResponse changeRol(@PathVariable Long id, @RequestBody ChangeRolRequest request) {
        return KullaniciResponse.from(kullaniciService.changeRol(id, request.rol()));
    }

    @Operation(
            summary = "Kullaniciyi siler",
            description = "Sistemdeki son ADMIN silinemez (CONFLICT_LAST_ADMIN).")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Kullanici silindi"),
        @ApiResponse(
                responseCode = "404",
                description = "Kullanici bulunamadi (NOT_FOUND_USER)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Son ADMIN silinemez (CONFLICT_LAST_ADMIN)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        kullaniciService.deleteKullanici(id);
        return ResponseEntity.noContent().build();
    }
}
