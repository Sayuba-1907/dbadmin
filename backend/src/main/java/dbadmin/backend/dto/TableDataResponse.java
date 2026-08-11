package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/tables/{id}/data} govdesi — DBeaver'daki "Data" sekmesinin karsiligi
 * (requirement notu 7). Metadata'dan (Tablo/Kolon) DEGIL, gercek Postgres tablosundan
 * {@code SELECT *} ile okunur; bkz. {@link dbadmin.backend.service.TableDataService}.
 * <p>
 * {@code columns}, o anki gercek tablo semasindan ({@code ResultSetMetaData}) gelir — Tablo/Kolon
 * metadata'siyla driftmis olsa bile (ör. elle yapilmis bir DDL) burada gorunen HER ZAMAN gercek
 * tablodur, DBeaver'in yaptigi da bu.
 */
public record TableDataResponse(
        @Schema(description = "Gercek tablodaki kolon adlari, sirali.") List<String> columns,
        @Schema(description = "Sayfa icindeki satirlar — her satir kolon adi -> deger eslemesi.")
                List<Map<String, Object>> rows,
        @Schema(description = "Tablodaki TOPLAM satir sayisi (sayfa degil) — pagination icin.")
                long totalRows) {
}
