package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * {@code PATCH /api/tables/{id}/data} govdesi — {@code pk} guncellenecek satiri PRIMARY KEY
 * kolonlariyla bulur (tablonun butun PK kolonlarini icermeli), {@code values} degistirilecek
 * kolon adi -> yeni deger eslemesidir. PK kolonlari {@code values} icinde OLAMAZ.
 */
public record UpdateRowRequest(
        @Schema(description = "Tablonun PRIMARY KEY kolon adi -> mevcut deger eslemesi (satiri bulmak icin).",
                example = "{\"calisan_no\": 3}")
                Map<String, Object> pk,
        @Schema(description = "Degistirilecek kolon adi -> yeni deger. PK kolonlari burada olamaz.",
                example = "{\"maas\": 55000}")
                Map<String, Object> values) {
}
