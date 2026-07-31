package dbadmin.backend.dto;

import dbadmin.backend.entity.Kolon;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * "Bu tag hangi tablo/kolonda kullaniliyor" sorusunun cevabindaki tek bir satir —
 * {@code GET /api/tags/{id}/kolonlar} bunun listesini doner. Kolon'un tag'i zaten belli oldugu
 * icin (yolun {@code id} parametresi) burada tekrar donmuyoruz; sadece o kolonu ve icinde
 * bulundugu tabloyu/schema'yi tanimlayan alanlar var.
 */
public record KolonUsageResponse(
        @Schema(description = "Kolonun bulundugu tablonun id'si.", example = "3") Long tabloId,
        @Schema(description = "Kolonun bulundugu tablonun adi.", example = "ogrenciler") String tabloName,
        @Schema(description = "Tablonun bulundugu schema'nin adi.", example = "okul") String schemaName,
        @Schema(description = "Kolonun id'si.", example = "12") Long kolonId,
        @Schema(description = "Kolonun adi.", example = "tc_no") String kolonName) {

    public static KolonUsageResponse from(Kolon kolon) {
        return new KolonUsageResponse(
                kolon.getTablo().getId(),
                kolon.getTablo().getName(),
                kolon.getTablo().getSchema().getName(),
                kolon.getId(),
                kolon.getName());
    }
}
