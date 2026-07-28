package dbadmin.backend.dto;

import dbadmin.backend.service.KolonTanimi;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir kolon tanimlamak icin request govdesi — hem tablo olustururken hem de "kolon ekle"
 * endpoint'inde kullanilir.
 */
public record CreateKolonRequest(
        @Schema(
                        description = "Kolonun adi. 2-30 karakter, kucuk harf/rakam/alt cizgi, buyuk harfle "
                                + "baslayamaz. Ayni tablo icinde benzersiz olmali.",
                        example = "yas",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(
                        description = "Kolonun veri tipi. Sadece su 4 degerden biri kabul edilir; "
                                + "olusturulduktan sonra degistirilemez.",
                        allowableValues = {"numeric", "text", "datetime", "boolean"},
                        example = "numeric",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String type,
        @Schema(
                        description = "Kolona baglanacak etiketin id'si (bkz. POST /api/tags). Bos "
                                + "birakilirsa kolon etiketsiz olusturulur.",
                        example = "1",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long tagId,
        @Schema(
                        description = "Bu kolonun (kullanicinin gozunde) birincil anahtar oldugunu isaretler. "
                                + "Gercek tablonun PRIMARY KEY'i her zaman otomatik eklenen 'id' kolonudur, bu "
                                + "alan onu degistirmez — ama true olan kolon(lar) uzerine gercek bir Postgres "
                                + "UNIQUE constraint kurulur (composite: birden fazla kolon true ise hepsi "
                                + "birlikte unique olur). Bos birakilirsa false sayilir.",
                        example = "false",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Boolean primaryKey) {

    /** primaryKey belirtilmedigi cagrilar icin (mevcut testler/cagiranlar) kisayol: varsayilan false. */
    public CreateKolonRequest(String name, String type, Long tagId) {
        this(name, type, tagId, false);
    }

    /** DTO'yu service katmaninin bekledigi ic tipe ({@link KolonTanimi}) cevirir. primaryKey gonderilmezse false sayilir. */
    public KolonTanimi toKolonTanimi() {
        return new KolonTanimi(name, type, tagId, primaryKey != null && primaryKey);
    }
}
