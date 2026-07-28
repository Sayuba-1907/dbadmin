package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * POST /api/tablolar icin request govdesi: "bu isimde tablo kur, icine bu kolonlari koy".
 * {@code schemaId} zorunlu — tablonun hangi schema'ya kurulacagi acikca belirtilmeli (bkz.
 * {@link dbadmin.backend.service.TabloService#createTablo}).
 */
public record CreateTabloRequest(
        @Schema(
                        description = "Tablonun adi. 2-30 karakter, kucuk harf/rakam/alt cizgi, buyuk harfle "
                                + "baslayamaz. Uygulama genelinde benzersiz olmali (baska bir schema'da bile "
                                + "olsa ayni isim kullanilamaz).",
                        example = "ogrenciler",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name,
        @Schema(
                        description = "Tablonun kurulacagi schema'nin id'si. Zorunlu; GET "
                                + "/api/schemalar ile listelenen schema'lardan biri olmali.",
                        example = "1",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long schemaId,
        @Schema(description = "Tabloya baslangicta eklenecek kolonlarin listesi. En az bir kolon zorunlu "
                        + "— bos/null listeyle tablo olusturulamaz.",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<CreateKolonRequest> kolonlar) {
}
