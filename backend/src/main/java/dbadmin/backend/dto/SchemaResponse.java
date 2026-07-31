package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** API'nin disari verdigi Schema govdesi — {@link TabloResponse} ile ayni mantik. */
public record SchemaResponse(
        @Schema(description = "Schema'nin id'si.", example = "1") Long id,
        @Schema(description = "Schema'nin adi.", example = "ogrenciler") String name,
        @Schema(description = "Schema icindeki tablo sayisi.", example = "5") long tabloSayisi) { // Yeni alan eklendi

    // from metodu artik tablo sayisini da disaridan parametre olarak aliyor
    public static SchemaResponse from(dbadmin.backend.entity.Schema schema, long tabloSayisi) {
        return new SchemaResponse(schema.getId(), schema.getName(), tabloSayisi);
    }
}