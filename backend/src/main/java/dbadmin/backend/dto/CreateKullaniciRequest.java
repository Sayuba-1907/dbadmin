package dbadmin.backend.dto;

import dbadmin.backend.entity.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/** {@code POST /api/kullanicilar} istek govdesi — sadece ADMIN cagirabilir. */
public record CreateKullaniciRequest(
        @Schema(description = "Kullanici adi. 3-30 karakter, harf/rakam/alt cizgi.", example = "ayse",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String kullaniciAdi,
        @Schema(description = "Duz metin parola, en az 8 karakter. Sunucuda BCrypt ile hash'lenir.",
                        example = "gizliParola1", requiredMode = Schema.RequiredMode.REQUIRED)
                String parola,
        @Schema(description = "Rol. Verilmezse VIEWER kabul edilir.", example = "EDITOR") Rol rol) {
}
