package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@link TableUpdateRequest#columnsToUpdate()} listesinin tek bir satiri: var olan bir
 * kolonun ismini/etiketini/PK isaretini olmasi gereken NIHAI haline getirir.
 * <p>
 * Dikkat: bu bir "patch" degil — bu listede yer alan bir kolon icin uc alan da (isim, tag, PK)
 * her zaman gonderilir, hangi alanin gercekten degistigi onemli degildir. Boylece "bu alan
 * gonderilmedi mi yoksa bilerek null mu" belirsizligi hic olusmaz: kolon listede yer aliyorsa,
 * buradaki degerler onun yeni gercegi olur.
 */
public record ColumnUpdateRequest(
        @Schema(description = "Guncellenecek kolonun id'si.", example = "5",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long columnId,
        @Schema(description = "Kolonun nihai adi (degismediyse bile gonderilmeli).",
                        example = "tc_no", requiredMode = Schema.RequiredMode.REQUIRED)
                String newName,
        @Schema(description = "Kolonun nihai etiket id'si. null ise etiketsiz.", example = "1",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long newTagId,
        @Schema(description = "Kolonun nihai birincil anahtar isareti.", example = "false",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                boolean newPrimaryKey) {
}
