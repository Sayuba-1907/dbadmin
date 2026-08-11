package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * {@code POST /api/tables/{id}/data} govdesi — eklenecek TEK satirin kolon adi -> deger
 * eslemesi. Verilmeyen kolonlar DB'nin varsayilanina (varsa) ya da NULL'a duser.
 */
public record InsertRowRequest(
        @Schema(description = "Kolon adi -> deger. Sadece tabloda gercekten var olan kolonlar kabul edilir.",
                example = "{\"ders_adi\": \"Matematik\", \"kredi\": 4}")
                Map<String, Object> values) {
}
