package dbadmin.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dbadmin.backend.dto.ErrorExamples;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link ErrorExamples}'taki 32 sabit elle yazildi (bkz. ErrorCodeRegistry) — bu test her
 * ornegin JSON'undaki {@code errorCode}'un {@link ErrorCodeRegistry}'deki kayitla birebir
 * aynı oldugunu, ilk 3 hanenin ayni JSON'daki {@code status} ile eslestigini, ve hicbir
 * errorCode'un tekrar etmedigini dogrular — elle girilen 32 sayida bir kopyala-yapistir
 * hatasi (yanlis sayi, kayan bir hane) olursa burada patlar.
 */
class ErrorCodeRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyExampleErrorCodeMatchesRegistryAndOwnStatus() throws Exception {
        Set<Integer> seen = new HashSet<>();
        for (Field field : ErrorExamples.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String json = (String) field.get(null);
            JsonNode node = MAPPER.readTree(json);
            String code = node.get("code").asString();
            int errorCode = node.get("errorCode").asInt();
            int status = node.get("status").asInt();

            assertEquals(
                    ErrorCodeRegistry.numberFor(code),
                    errorCode,
                    "ErrorExamples." + field.getName() + " errorCode does not match ErrorCodeRegistry");
            assertEquals(
                    status,
                    Integer.parseInt(String.valueOf(errorCode).substring(0, 3)),
                    "ErrorExamples." + field.getName() + " errorCode's first 3 digits must match its own status");
            assertTrue(
                    seen.add(errorCode),
                    "errorCode " + errorCode + " is used by more than one ErrorExamples constant");
        }
    }
}
