package dbadmin.backend.dto;

import dbadmin.backend.service.KolonTanimi;

/**
 * Bir kolon tanimlamak icin request govdesi — hem tablo olustururken hem de "kolon ekle"
 * endpoint'inde kullanilir.
 */
public record CreateKolonRequest(String name, String type, Long tagId, Boolean primaryKey) {

    /** primaryKey belirtilmedigi cagrilar icin (mevcut testler/cagiranlar) kisayol: varsayilan false. */
    public CreateKolonRequest(String name, String type, Long tagId) {
        this(name, type, tagId, false);
    }

    /** DTO'yu service katmaninin bekledigi ic tipe ({@link KolonTanimi}) cevirir. primaryKey gonderilmezse false sayilir. */
    public KolonTanimi toKolonTanimi() {
        return new KolonTanimi(name, type, tagId, primaryKey != null && primaryKey);
    }
}
