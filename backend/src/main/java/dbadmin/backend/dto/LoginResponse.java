package dbadmin.backend.dto;

import dbadmin.backend.entity.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Basarili girisin cevabi. Token'i istemci saklar ve sonraki her istekte
 * {@code Authorization: Bearer <token>} basliginda geri gonderir.
 * <p>
 * Rol de doniliyor ki frontend arayuzu ona gore kurabilsin (ör. VIEWER'a "Sil" butonunu hic
 * gostermemek). Bu yalnizca gorsel kolaylik — asil yetki karari her zaman sunucuda verilir,
 * cunku istemciden gelen hicbir sey guvenilir degildir.
 */
public record LoginResponse(
        @Schema(description = "Imzali JWT. Sonraki isteklerde Authorization basliginda gonderilir.")
                String token,
        @Schema(description = "Giris yapan kullanicinin adi.", example = "admin") String kullaniciAdi,
        @Schema(description = "Kullanicinin rolu.", example = "ADMIN") Rol rol) {
}
