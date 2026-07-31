package dbadmin.backend.dto;

import dbadmin.backend.entity.Tablo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * API'nin disari verdigi Tablo govdesi — {@link Tablo} entity'sinin kendisi degil.
 * Neden ayri bir tip: entity'yi dogrudan JSON'a cevirmeye kalksak, Kolon entity'sinin
 * icindeki Tablo referansi yuzunden sonsuz donguye (tablo -> kolon -> tablo -> ...) girerdi.
 * Bu record'da geriye referans olmadigi icin o sorun hic yasanmaz.
 */
public record TabloResponse(
        @Schema(description = "Tablonun id'si.", example = "1") Long id,
        @Schema(description = "Tablonun adi.", example = "ogrenciler") String name,
        @Schema(description = "Tablonun ait oldugu schema'nin id'si.", example = "1") Long schemaId,
        @Schema(description = "Tablonun ait oldugu schema'nin adi.", example = "ogrenciler") String schemaName,
        @Schema(description = "Tablonun kolonlarinin listesi.") List<KolonResponse> kolonlar,
        @Schema(description = "Tabloda ya da kolonlarindan birinde en son ne zaman degisiklik "
                        + "yapildigi (olusturma, yeniden adlandirma, schema tasima, kolon "
                        + "ekleme/silme/yeniden adlandirma/etiket degistirme dahil). Eski satirlarda "
                        + "(bu alan eklenmeden once olusturulmus) null olabilir.",
                        example = "2026-07-28T10:15:30Z")
                Instant updatedAt) {

    /** Entity'den DTO'ya cevirici — entity'yi controller'a hic sizdirmadan burada donusturuyoruz. */
    public static TabloResponse from(Tablo tablo) {
        List<KolonResponse> kolonlar = tablo.getKolonlar().stream()
                .map(KolonResponse::from)
                .toList();
        dbadmin.backend.entity.Schema schema = tablo.getSchema();
        return new TabloResponse(
                tablo.getId(), tablo.getName(),
                schema != null ? schema.getId() : null,
                schema != null ? schema.getName() : null,
                kolonlar,
                tablo.getUpdatedAt());
    }
}
