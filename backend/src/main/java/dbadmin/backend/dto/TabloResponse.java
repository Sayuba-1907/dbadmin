package dbadmin.backend.dto;

import dbadmin.backend.entity.Tablo;
import java.util.List;

/**
 * API'nin disari verdigi Tablo govdesi — {@link Tablo} entity'sinin kendisi degil.
 * Neden ayri bir tip: entity'yi dogrudan JSON'a cevirmeye kalksak, Kolon entity'sinin
 * icindeki Tablo referansi yuzunden sonsuz donguye (tablo -> kolon -> tablo -> ...) girerdi.
 * Bu record'da geriye referans olmadigi icin o sorun hic yasanmaz.
 */
public record TabloResponse(Long id, String name, List<KolonResponse> kolonlar) {

    /** Entity'den DTO'ya cevirici — entity'yi controller'a hic sizdirmadan burada donusturuyoruz. */
    public static TabloResponse from(Tablo tablo) {
        List<KolonResponse> kolonlar = tablo.getKolonlar().stream()
                .map(KolonResponse::from)
                .toList();
        return new TabloResponse(tablo.getId(), tablo.getName(), kolonlar);
    }
}
