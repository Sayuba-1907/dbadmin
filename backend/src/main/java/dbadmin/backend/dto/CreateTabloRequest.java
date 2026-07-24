package dbadmin.backend.dto;

import java.util.List;

/** POST /api/tablolar icin request govdesi: "bu isimde tablo kur, icine bu kolonlari koy". */
public record CreateTabloRequest(String name, List<CreateKolonRequest> kolonlar) {
}
