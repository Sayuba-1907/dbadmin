package dbadmin.backend.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.CreateSchemaRequest;
import dbadmin.backend.dto.CreateTagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Backend i18n'inin sozlesmesini kilitler: hata mesaji {@code Accept-Language} basligina gore
 * degisir, ama {@code code} ve {@code status} her dilde AYNI kalir — frontend kararini koda
 * gore verdigi icin kodun dile gore degismemesi kritik.
 */
@AutoConfigureMockMvc
// Bu testler uclara HTTP uzerinden gidiyor, yani artik Spring Security'nin filtre
// zincirinden de geciyorlar — kimliksiz her istek 401 donerdi. @WithMockUser guvenlik
// baglamina hazir bir kullanici koyar (gercek bir token uretmeye gerek kalmaz); ADMIN
// secildi cunku bu siniflarin derdi yetki degil, uclarin kendi davranisi. Yetki
// kurallarinin kendisi ayrica SecurityRulesIntegrationTest'te sinaniyor.
@WithMockUser(roles = "ADMIN")
class ErrorMessageI18nIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void notFound_withTurkishAcceptLanguage_returnsTurkishMessage() throws Exception {
        mockMvc.perform(get("/api/tablolar/999999").header(HttpHeaders.ACCEPT_LANGUAGE, "tr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_TABLE")))
                .andExpect(jsonPath("$.message", is("Tablo bulunamadı: 999999")));
    }

    @Test
    void notFound_withEnglishAcceptLanguage_returnsEnglishMessage() throws Exception {
        mockMvc.perform(get("/api/tablolar/999999").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_TABLE")))
                .andExpect(jsonPath("$.message", is("tablo not found: 999999")));
    }

    @Test
    void notFound_withoutAcceptLanguage_fallsBackToEnglish() throws Exception {
        mockMvc.perform(get("/api/tablolar/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("tablo not found: 999999")));
    }

    @Test
    void notFound_withUnsupportedLanguage_fallsBackToEnglish() throws Exception {
        // Almanca cevirimiz yok; sunucunun kendi diline degil, varsayilan dosyaya dusmeli
        // (spring.messages.fallback-to-system-locale=false).
        mockMvc.perform(get("/api/tablolar/999999").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("tablo not found: 999999")));
    }

    @Test
    void validationError_isTranslated() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTagRequest("Buyuk"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_INVALID_TAG_NAME")))
                .andExpect(jsonPath("$.message", containsString("Etiket adı")));
    }

    @Test
    void conflictError_isTranslatedAndPlaceholderIsFilled() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateTagRequest("i18n_tag"));
        mockMvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // Ayni isimle ikinci kez: 409 + Turkce mesaj, icinde etiket adi dolu olmali.
        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT_DUPLICATE_TAG_NAME")))
                .andExpect(jsonPath("$.message", is("'i18n_tag' adında bir etiket zaten var")))
                // Yer tutucu gercekten dolmus olmali — sablon oldugu gibi sizmamali.
                .andExpect(jsonPath("$.message", containsString("i18n_tag")))
                .andExpect(jsonPath("$.details.name", is("i18n_tag")));
    }

    @Test
    void errorCodeAndStatus_areIdenticalAcrossLanguages() throws Exception {
        // Dil sadece "message" alanini etkilemeli; makinenin baktigi alanlar sabit kalmali.
        for (String lang : new String[] {"tr", "en", "de"}) {
            mockMvc.perform(get("/api/tablolar/999999").header(HttpHeaders.ACCEPT_LANGUAGE, lang))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)))
                    .andExpect(jsonPath("$.error", is("Not Found")))
                    .andExpect(jsonPath("$.code", is("NOT_FOUND_TABLE")))
                    .andExpect(jsonPath("$.details.id", is("999999")));
        }
    }

    // ---------------------------------------------------------------------
    // Asagidakiler uygulamanin kendi exception'larindan degil, Spring'in girdi/istek
    // katmaninin kendiliginden firlattigi durumlar (bkz. GlobalExceptionHandler'daki ek
    // handler'lar). Bunlar eklenmeden once Spring'in varsayilan gövdesine ({@code
    // {"status":..,"error":..,"path":..}}, code/message olmadan) dusuluyordu — frontend'in
    // {@code err.code}'a bakan cevirisi bu durumlarda bos kaliyordu.

    @Test
    void invalidPathParam_returnsOwnCodeInsteadOfDefaultSpringBody() throws Exception {
        mockMvc.perform(get("/api/tablolar/{id}", "abc").header(HttpHeaders.ACCEPT_LANGUAGE, "tr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_INVALID_PATH_PARAM")))
                .andExpect(jsonPath("$.message", containsString("abc")))
                .andExpect(jsonPath("$.details.parameter", is("id")));
    }

    @Test
    void malformedJsonBody_returnsOwnCode() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bozuk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_MALFORMED_BODY")));
    }

    @Test
    void unknownPath_returnsOwnCode() throws Exception {
        mockMvc.perform(get("/api/yokboyle"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_ENDPOINT")));
    }

    @Test
    void unsupportedHttpMethod_returnsOwnCode() throws Exception {
        mockMvc.perform(put("/api/tablolar"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code", is("VALIDATION_METHOD_NOT_ALLOWED")))
                .andExpect(jsonPath("$.details.method", is("PUT")));
    }

    @Test
    void primaryKeyOnColumnWithNullValues_returnsConflictInsteadOf500() throws Exception {
        // Ayni senaryo TabloServiceIntegrationTest'te servis seviyesinde de kilitli
        // (changeKolonPrimaryKey_columnWithNullValues_isRejectedAndLeavesMetadataUnchanged) —
        // burada HTTP katmaninin bunu artik Spring'in cigplak 500'u degil, kendi kodu ve
        // 409 status'uyle dondurdugunu dogruluyoruz.
        String schemaResponse = mockMvc.perform(post("/api/schemalar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSchemaRequest("i18n_pk_sema"))))
                .andReturn().getResponse().getContentAsString();
        long schemaId = objectMapper.readTree(schemaResponse).path("id").asLong();

        String createResponse = mockMvc.perform(post("/api/tablolar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"i18n_pk_tablo\",\"schemaId\":" + schemaId
                                + ",\"kolonlar\":[{\"name\":\"kolona\",\"type\":\"text\",\"primaryKey\":true},"
                                + "{\"name\":\"kolonb\",\"type\":\"text\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        long tabloId = created.path("id").asLong();
        long kolonbId = -1;
        for (JsonNode k : created.path("kolonlar")) {
            if ("kolonb".equals(k.path("name").asString())) {
                kolonbId = k.path("id").asLong();
            }
        }

        // "kolonb"'yi NULL birakan bir satir ekliyoruz — PRIMARY KEY kolonlari otomatik
        // NOT NULL yapildigi icin ADD CONSTRAINT bu satir yuzunden reddedilecek.
        jdbcTemplate.update("INSERT INTO \"i18n_pk_sema\".\"i18n_pk_tablo\" (kolona) VALUES ('x')");

        mockMvc.perform(patch("/api/tablolar/{id}/kolonlar/{kolonId}/primary-key", tabloId, kolonbId)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "tr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryKey\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT_COLUMN_NOT_UNIQUE")))
                .andExpect(jsonPath("$.message", containsString("çelişiyor")));
    }
}
