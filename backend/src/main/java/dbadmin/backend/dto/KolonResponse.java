package dbadmin.backend.dto;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API'nin disari verdigi Kolon govdesi. Tag'i tum {@code Tag} nesnesi olarak degil
 * {@code tagId} + {@code tagName} olarak duz (flatten) veriyoruz — frontend'in ekstra
 * bir nested object'i parse etmesine gerek kalmiyor.
 */
public record KolonResponse(
        @Schema(description = "Kolonun id'si.", example = "1") Long id,
        @Schema(description = "Kolonun adi.", example = "yas") String name,
        @Schema(description = "Kolonun tipi.", allowableValues = {"numeric", "text", "datetime", "boolean"},
                        example = "numeric")
                String type,
        @Schema(description = "Bagli etiketin id'si, etiketsizse null.", example = "1") Long tagId,
        @Schema(description = "Bagli etiketin adi, etiketsizse null.", example = "onemli") String tagName,
        @Schema(description = "Bu kolonun birincil anahtar olarak isaretlenip isaretlenmedigi (bkz. "
                        + "CreateKolonRequest.primaryKey aciklamasi).", example = "false")
                boolean primaryKey) {

    public static KolonResponse from(Kolon kolon) {
        Tag tag = kolon.getTag();
        Long tagId = tag == null ? null : tag.getId();
        String tagName = tag == null ? null : tag.getName();
        return new KolonResponse(
                kolon.getId(), kolon.getName(), kolon.getType(), tagId, tagName, kolon.isPrimaryKey());
    }
}
