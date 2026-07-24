package dbadmin.backend.dto;

import dbadmin.backend.service.KolonTanimi;

/**
 * Bir kolon tanimlamak icin request govdesi — hem tablo olustururken hem de "kolon ekle"
 * endpoint'inde kullanilir.
 */
public record CreateKolonRequest(String name, String type, Long tagId) {

    /** DTO'yu service katmaninin bekledigi ic tipe ({@link KolonTanimi}) cevirir. */
    public KolonTanimi toKolonTanimi() {
        return new KolonTanimi(name, type, tagId);
    }
}
