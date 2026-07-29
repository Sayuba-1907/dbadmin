package dbadmin.backend.dto;

import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.entity.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir kullanicinin disariya donen hali. Parola hash'i <b>bilerek yok</b> — entity'yi
 * dogrudan dondurseydik hash de JSON'a girerdi.
 */
public record KullaniciResponse(
        @Schema(description = "Kullanicinin id'si.", example = "1") Long id,
        @Schema(description = "Kullanici adi.", example = "admin") String kullaniciAdi,
        @Schema(description = "Rolu.", example = "ADMIN") Rol rol) {

    public static KullaniciResponse from(Kullanici kullanici) {
        return new KullaniciResponse(kullanici.getId(), kullanici.getKullaniciAdi(), kullanici.getRol());
    }
}
