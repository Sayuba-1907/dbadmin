package dbadmin.backend.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Iki constructor var, kafa karistirmasin diye: asagidaki 2 parametreli olan
 * {@link dbadmin.backend.service.SchemaService#getSchemaList()}'in bos bir DTO acip sonra
 * {@link #addTableSummaryToList} ile doldurdugu "biriktirici" yol. 4 parametreli, {@code @JsonCreator}
 * isaretli olan ise SADECE Jackson icin — {@code schemaWorkspace} Redis cache'inden JSON'u geri
 * nesneye cevirirken kullaniliyor (bkz. TableSummaryDTO'daki ayni gerekce: bu olmadan cache hicbir
 * zaman gercekten okunmuyordu, hep sessizce DB'ye dusuluyordu).
 */
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

    @JsonCreator
    public SchemaResponseDTO(
            @JsonProperty("schemaId") Long schemaId,
            @JsonProperty("schemaName") String schemaName,
            @JsonProperty("tableCount") int tableCount,
            @JsonProperty("tableResponseList") List<TableSummaryDTO> tableResponseList) {
        this.schemaId = schemaId;
        this.schemaName = schemaName;
        this.tableResponseList = tableResponseList != null ? tableResponseList : new ArrayList<>();
        this.tableCount = tableCount;
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
