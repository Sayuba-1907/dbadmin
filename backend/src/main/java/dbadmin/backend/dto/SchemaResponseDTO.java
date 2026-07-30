package dbadmin.backend.dto;


import java.util.ArrayList;
import java.util.List;

public class SchemaResponseDTO
{
    private Long schemaId;
    private String schemaName;
    private int tableCount;
    List<TableSummaryDTO> tableResponseList;


    public SchemaResponseDTO(Long schemaId,String schemaName){
        this.schemaId=schemaId;
        this.schemaName=schemaName;
        this.tableResponseList=new ArrayList<>();
        this.tableCount=tableResponseList.size();
    }

    public Long getSchemaId() {
        return schemaId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public int getTableCount() {
        return tableCount;
    }

    public List<TableSummaryDTO> getTableResponseList() {
        return tableResponseList;
    }

    public void addTableSummaryToList(TableSummaryDTO tableSummaryDTO){
        this.tableResponseList.add(tableSummaryDTO);
        this.tableCount=this.tableResponseList.size();
    }
    public void addTableSummaryListToList(List<TableSummaryDTO>tableSummaryDTOList){
        this.tableResponseList.addAll(tableSummaryDTOList);
        this.tableCount=this.tableResponseList.size();
    }
}
