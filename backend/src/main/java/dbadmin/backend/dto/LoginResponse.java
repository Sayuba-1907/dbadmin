package dbadmin.backend.dto;

import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Basarili girisin cevabi. Token'i istemci saklar ve sonraki her istekte
 * {@code Authorization: Bearer <token>} basliginda geri gonderir.
 * <p>
 * Rol de doniliyor ki frontend arayuzu ona gore kurabilsin (ör. VIEWER'a "Sil" butonunu hic
 * gostermemek). Bu yalnizca gorsel kolaylik — asil yetki karari her zaman sunucuda verilir,
 * cunku istemciden gelen hicbir sey guvenilir degildir.
 * <p>
 * {@code token}, {@code PATCH /api/auth/me} kullanici adini degistirdiginde de doldurulur:
 * JWT'nin subject'i (username) artik gecersiz oldugu icin (bkz. JwtService), kullaniciyi
 * logout etmek yerine sunucu tarafinda hemen taze bir token uretilir — frontend bunu sessizce
 * eskisinin yerine yazar, kullanici tekrar giris yapmak zorunda kalmaz.
 */
public record LoginResponse(
        @Schema(description = "Imzali JWT. Sadece login ve username degisikliginde dolu gelir, aksi halde null.")
                String token,
        @Schema(description = "Kullanicinin id'si.", example = "1") Long id,
        @Schema(description = "Kullanici adi.", example = "admin") String username,
        @Schema(description = "Ad soyad — bossa frontend username'i gosterir.", example = "Salih Bayraktar")
                String fullName,
        @Schema(description = "Kullanicinin rolu.", example = "ADMIN") Role role,
        @Schema(description = "Profil fotografi yuklenmis mi — true ise GET /api/auth/me/avatar'dan cekilebilir.")
                boolean hasAvatar) {

    public static LoginResponse of(String token, User user) {
        return new LoginResponse(
                token, user.getId(), user.getUsername(), user.getFullName(), user.getRole(),
                user.getAvatarKey() != null);
    }
}
