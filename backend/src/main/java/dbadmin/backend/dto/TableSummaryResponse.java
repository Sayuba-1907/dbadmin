package dbadmin.backend.dto;

import dbadmin.backend.entity.DataTable;

public record TableSummaryResponse(
        Long id,
        String name,
        int columnCount
) {
    public static TableSummaryResponse from(DataTable table) {
        // Kolonlar null gelirse hata almamak için ufak bir kontrol yapıyoruz
        int columnCount = (table.getColumns() != null) ? table.getColumns().size() : 0;
        return new TableSummaryResponse(table.getId(), table.getName(), columnCount);
    }
}
