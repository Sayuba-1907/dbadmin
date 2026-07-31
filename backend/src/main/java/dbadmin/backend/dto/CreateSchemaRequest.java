package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** POST /api/schemalar icin request govdesi: "bu isimde schema kur". */
public record CreateSchemaRequest(
        @Schema(
                        description = "Yeni schema'nin adi. 2-30 karakter, kucuk harf/rakam/alt cizgi, "
                                + "buyuk harfle baslayamaz. 'public' rezerve — Postgres'in altyapi "
                                + "schema'si oldugu icin bu isimle yeni schema olusturulamaz.",
                        example = "raporlama",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name) {
}
