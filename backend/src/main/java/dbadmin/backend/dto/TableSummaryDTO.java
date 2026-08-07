package dbadmin.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code @JsonCreator}/{@code @JsonProperty}: sadece web katmani (controller donus tipi) icin
 * degil, {@code schemaWorkspace} Redis cache'inin JSON'dan geri nesne kurabilmesi icin de
 * gerekli — bunlar olmadan Jackson bu siniftan sadece yazabiliyor, GERI OKUYAMIYORDU (varsayilan
 * parametresiz constructor yok). Cache'e yazma calisiyor gibi gorunuyordu ama her okuma sessizce
 * (fail-open sayesinde) basarisiz olup DB'ye dusuyordu — cache hicbir zaman gercekten calismiyordu.
 */
public class TableSummaryDTO {

    private Long id;
    private String name;
    private int columnCount;
    private Long schemaId;

    @JsonCreator
    public TableSummaryDTO(
            @JsonProperty("id") Long id,
            @JsonProperty("name") String name,
            @JsonProperty("columnCount") int columnCount,
            @JsonProperty("schemaId") Long schemaId) {
        this.id = id;
        this.name = name;
        this.columnCount = columnCount;
        this.schemaId=schemaId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public Long getSchemaId() {
        return schemaId;
    }
}
