package dbadmin.backend.dto;

import dbadmin.backend.entity.DataColumn;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * "Bu tag hangi tablo/kolonda kullaniliyor" sorusunun cevabindaki tek bir satir —
 * {@code GET /api/tags/{id}/columns} bunun listesini doner. Kolon'un tag'i zaten belli oldugu
 * icin (yolun {@code id} parametresi) burada tekrar donmuyoruz; sadece o kolonu ve icinde
 * bulundugu tabloyu/schema'yi tanimlayan alanlar var.
 */
public record ColumnUsageResponse(
        @Schema(description = "Kolonun bulundugu tablonun id'si.", example = "3") Long tableId,
        @Schema(description = "Kolonun bulundugu tablonun adi.", example = "ogrenciler") String tableName,
        @Schema(description = "Tablonun bulundugu schema'nin adi.", example = "okul") String schemaName,
        @Schema(description = "Kolonun id'si.", example = "12") Long columnId,
        @Schema(description = "Kolonun adi.", example = "tc_no") String columnName) {

    public static ColumnUsageResponse from(DataColumn column) {
        return new ColumnUsageResponse(
                column.getTable().getId(),
                column.getTable().getName(),
                column.getTable().getSchema().getName(),
                column.getId(),
                column.getName());
    }
}
