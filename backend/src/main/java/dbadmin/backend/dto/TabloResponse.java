package dbadmin.backend.dto;

import dbadmin.backend.entity.Tablo;
import java.util.List;
//TabloResponse, içindeki kolonlarıyla birlikte bir tabloyu dış dünyaya emniyetli ve temiz bir şekilde sunan ana cevap tabağıdır.
// API-facing shape for Tablo: no back-reference to Kolon's Tablo field,
// so this can never recurse the way returning the entity directly would.
public record TabloResponse(Long id, String name, List<KolonResponse> kolonlar) {

    public static TabloResponse from(Tablo tablo) {
        List<KolonResponse> kolonlar = tablo.getKolonlar().stream()
                .map(KolonResponse::from)
                .toList();
        return new TabloResponse(tablo.getId(), tablo.getName(), kolonlar);
    }
}
