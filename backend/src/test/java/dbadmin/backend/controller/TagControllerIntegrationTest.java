package dbadmin.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.CreateKolonRequest;
import dbadmin.backend.dto.CreateSchemaRequest;
import dbadmin.backend.dto.CreateTabloRequest;
import dbadmin.backend.dto.CreateTagRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
// Bu testler uclara HTTP uzerinden gidiyor, yani artik Spring Security'nin filtre
// zincirinden de geciyorlar — kimliksiz her istek 401 donerdi. @WithMockUser guvenlik
// baglamina hazir bir kullanici koyar (gercek bir token uretmeye gerek kalmaz); ADMIN
// secildi cunku bu siniflarin derdi yetki degil, uclarin kendi davranisi. Yetki
// kurallarinin kendisi ayrica SecurityRulesIntegrationTest'te sinaniyor.
@WithMockUser(roles = "ADMIN")
class TagControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Testlerin tablo kurdugu schema — "public" gecerli bir hedef degil. */
    private static final String TEST_SCHEMA = "tag_usage_sema";

    private Long testSchemaId;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @BeforeEach
    void ensureTestSchema() throws Exception {
        String listResponse = mockMvc.perform(get("/api/schemalar"))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode schema : objectMapper.readTree(listResponse)) {
            if (TEST_SCHEMA.equals(schema.get("name").asString())) {
                testSchemaId = schema.get("id").asLong();
                return;
            }
        }
        String createResponse = mockMvc.perform(post("/api/schemalar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateSchemaRequest(TEST_SCHEMA))))
                .andReturn().getResponse().getContentAsString();
        testSchemaId = objectMapper.readTree(createResponse).get("id").asLong();
    }

    @Test
    void create_returns201() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("gizli"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("gizli")));
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("pii"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("pii"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void create_invalidName_returns400() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("Buyuk"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void list_returns200() throws Exception {
        mockMvc.perform(get("/api/tags")).andExpect(status().isOk());
    }

    @Test
    void usage_unknownTag_returns404() throws Exception {
        mockMvc.perform(get("/api/tags/999999/kolonlar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_TAG")));
    }

    @Test
    void usage_tagNotUsedByAnyColumn_returnsEmptyList() throws Exception {
        String tagResponse = mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("kullanilmayan_tag"))))
                .andReturn().getResponse().getContentAsString();
        long tagId = objectMapper.readTree(tagResponse).get("id").asLong();

        mockMvc.perform(get("/api/tags/{id}/kolonlar", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void usage_returnsColumnsUsingTagWithTableAndSchemaInfo() throws Exception {
        String tagResponse = mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTagRequest("kullanilan_tag"))))
                .andReturn().getResponse().getContentAsString();
        long tagId = objectMapper.readTree(tagResponse).get("id").asLong();

        CreateTabloRequest createTablo = new CreateTabloRequest("tag_usage_tablo", testSchemaId,
                List.of(new CreateKolonRequest("tc_no", "text", tagId)));
        String tabloResponse = mockMvc.perform(post("/api/tablolar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createTablo)))
                .andReturn().getResponse().getContentAsString();
        JsonNode tablo = objectMapper.readTree(tabloResponse);
        long kolonId = tablo.get("kolonlar").get(0).get("id").asLong();

        mockMvc.perform(get("/api/tags/{id}/kolonlar", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].kolonId", is((int) kolonId)))
                .andExpect(jsonPath("$[0].kolonName", is("tc_no")))
                .andExpect(jsonPath("$[0].tabloId", is(tablo.get("id").asInt())))
                .andExpect(jsonPath("$[0].tabloName", is("tag_usage_tablo")))
                .andExpect(jsonPath("$[0].schemaName", is(TEST_SCHEMA)));
    }
}
