package dbadmin.backend.dto;

public class TableSummaryDTO {

    private Long id;
    private String name;
    private int columnCount;
    private Long schemaId;

    public TableSummaryDTO(Long id, String name, int columnCount, Long schemaId) {
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
