package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kolonun etiketini degistirme request'i. {@code tagId == null} ise etiket kaldirilir; doluysa o etikete baglanir. */
public record ChangeTagRequest(
        @Schema(
                        description = "Kolona baglanacak etiketin id'si. null gonderilirse kolondaki mevcut "
                                + "etiket kaldirilir.",
                        example = "1",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long tagId) {
}
