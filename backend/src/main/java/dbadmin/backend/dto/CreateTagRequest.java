package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** POST /api/tags icin request govdesi: sadece yeni etiketin adi. */
public record CreateTagRequest(
        @Schema(
                        description = "Yeni etiketin adi. 2-30 karakter, kucuk harf/rakam/alt cizgi, "
                                + "buyuk harfle baslayamaz. Uygulama genelinde benzersiz olmali.",
                        example = "onemli",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name) {
}
