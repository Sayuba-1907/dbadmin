package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** "Rename table" ve "rename column" endpoint'lerinin ortak request govdesi — ikisi de tek bir yeni isim alir. */
public record RenameRequest(
        @Schema(
                        description = "Yeni isim. 2-30 karakter, kucuk harf/rakam/alt cizgi, buyuk harfle "
                                + "baslayamaz.",
                        example = "ogrenciler_v2",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name) {
}
