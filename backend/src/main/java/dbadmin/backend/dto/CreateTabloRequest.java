package dbadmin.backend.dto;

import java.util.List;

/**
 * POST /api/tablolar icin request govdesi: "bu isimde tablo kur, icine bu kolonlari koy".
 * {@code schemaId} opsiyonel: null gelirse tablo varsayilan olarak "public" schema'ya kurulur
 * (bkz. {@link dbadmin.backend.service.TabloService#createTablo}).
 */
public record CreateTabloRequest(String name, Long schemaId, List<CreateKolonRequest> kolonlar) {
}
