package dbadmin.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.ChangePrimaryKeyRequest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.CreateColumnRequest;
import dbadmin.backend.dto.CreateSchemaRequest;
import dbadmin.backend.dto.CreateTableRequest;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TableUpdateRequest;
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

// Automates the same manual curl checks run earlier: every use case must
// come back with the HTTP status code the spec requires (201/200/400/404/
// 409/204), because the frontend colours its notifications off of that
// status. A regression here (e.g. a 500 instead of a 409) fails the build.
@AutoConfigureMockMvc
// Bu testler uclara HTTP uzerinden gidiyor, yani artik Spring Security'nin filtre
// zincirinden de geciyorlar — kimliksiz her istek 401 donerdi. @WithMockUser guvenlik
// baglamina hazir bir user koyar (gercek bir token uretmeye gerek kalmaz); ADMIN
// secildi cunku bu siniflarin derdi yetki degil, uclarin kendi davranisi. Yetki
// kurallarinin kendisi ayrica SecurityRulesIntegrationTest'te sinaniyor.
@WithMockUser(username = "admin", roles = "ADMIN")
class TableControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Testlerin table kurdugu schema — "public" gecerli bir hedef degil, bkz. SchemaService.RESERVED_SCHEMA_NAME. */
    private static final String TEST_SCHEMA = "ders_sema";

    private Long testSchemaId;

    private String json(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    /**
     * DataTable olusturmak icin schemaId zorunlu oldugundan testler once kendi schema'sini kurar —
     * bilerek API uzerinden, bu da GET/POST /api/schemas'i her testte bir kez dogrulamis olur.
     * Postgres tum testler arasinda paylasildigi icin schema zaten varsa yeniden kurulmaz.
     */
    @BeforeEach
    void ensureTestSchema() throws Exception {
        // GET /api/schemas artik sayfalanmis ({@code Page<SchemaResponse>}) doner — satirlar
        // artik JSON'un koku degil, "content" alaninin altinda. Buyuk bir size, paylasilan
        // test DB'sinde biriken tum schema'lar arasinda test schema'sini kacirmamak icin.
        String listResponse = mockMvc.perform(get("/api/schemas").param("size", "1000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode schema : objectMapper.readTree(listResponse).get("content")) {
            if (TEST_SCHEMA.equals(schema.get("name").asString())) {
                testSchemaId = schema.get("id").asLong();
                return;
            }
        }
        String createResponse = mockMvc.perform(post("/api/schemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateSchemaRequest(TEST_SCHEMA))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        testSchemaId = objectMapper.readTree(createResponse).get("id").asLong();
    }

    @Test
    void create_returns201() throws Exception {
        CreateTableRequest request = new CreateTableRequest("ders1", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("ders1")));
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        CreateTableRequest request = new CreateTableRequest("ders2", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));
        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void create_invalidName_returns400() throws Exception {
        CreateTableRequest request = new CreateTableRequest("Buyuk", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void create_unknownSchemaId_returns404() throws Exception {
        CreateTableRequest request = new CreateTableRequest("ders4", -1L,
                List.of(new CreateColumnRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void create_noSchemaId_returns400() throws Exception {
        CreateTableRequest request = new CreateTableRequest("ders5", null,
                List.of(new CreateColumnRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void listSchemalar_doesNotContainPublic() throws Exception {
        String response = mockMvc.perform(get("/api/schemas").param("size", "1000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode schema : objectMapper.readTree(response).get("content")) {
            assertNotEquals("public", schema.get("name").asString());
        }
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/tables/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void list_returns200() throws Exception {
        mockMvc.perform(get("/api/tables")).andExpect(status().isOk());
    }

    @Test
    void fullLifecycle_rename_addColumn_changeTag_deleteColumn_deleteTable() throws Exception {
        CreateTableRequest createRequest = new CreateTableRequest("ders3", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));
        String createResponse = mockMvc.perform(
                        post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tableId = objectMapper.readTree(createResponse).get("id").asLong();
        long kolonId = objectMapper.readTree(createResponse).get("columns").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/tables/{id}", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RenameRequest("ders3_yeni"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ders3_yeni")));

        mockMvc.perform(post("/api/tables/{id}/columns", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateColumnRequest("puan", "numeric", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/tables/{id}/columns/{kolonId}/tag", tableId, kolonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangeTagRequest(null))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/tables/{id}/columns/{kolonId}", tableId, kolonId))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tables/{id}", tableId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tables/{id}", tableId))
                .andExpect(status().isNotFound());
    }

    @Test
    void changeKolonPrimaryKey_togglesFlagAndIsVisibleInResponse() throws Exception {
        CreateTableRequest createRequest = new CreateTableRequest("ders_pk1", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));
        String createResponse = mockMvc.perform(
                        post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.columns[0].primaryKey", is(false)))
                .andReturn().getResponse().getContentAsString();
        long tableId = objectMapper.readTree(createResponse).get("id").asLong();
        long kolonId = objectMapper.readTree(createResponse).get("columns").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/tables/{id}/columns/{kolonId}/primary-key", tableId, kolonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePrimaryKeyRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryKey", is(true)));

        mockMvc.perform(get("/api/tables/{id}", tableId))
                .andExpect(jsonPath("$.columns[0].primaryKey", is(true)));

        mockMvc.perform(patch("/api/tables/{id}/columns/{kolonId}/primary-key", tableId, kolonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePrimaryKeyRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryKey", is(false)));
    }

    @Test
    void changeKolonPrimaryKey_unknownKolon_returns404() throws Exception {
        CreateTableRequest createRequest = new CreateTableRequest("ders_pk2", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));
        String createResponse = mockMvc.perform(
                        post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tableId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(patch("/api/tables/{id}/columns/{kolonId}/primary-key", tableId, -1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangePrimaryKeyRequest(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_COLUMN")));
    }

    @Test
    void applyChanges_renameAndAddColumn_returns200WithUpdatedTablo() throws Exception {
        CreateTableRequest createRequest = new CreateTableRequest("ders_pk3", testSchemaId,
                List.of(new CreateColumnRequest("ad", "text", null)));
        String createResponse = mockMvc.perform(
                        post("/api/tables").contentType(MediaType.APPLICATION_JSON).content(json(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tableId = objectMapper.readTree(createResponse).get("id").asLong();

        TableUpdateRequest updateRequest = new TableUpdateRequest("ders_pk3_yeni", null, null,
                List.of(new CreateColumnRequest("yeni_kolon", "numeric", null)), null);

        mockMvc.perform(patch("/api/tables/{id}/changes", tableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ders_pk3_yeni")))
                .andExpect(jsonPath("$.columns", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void applyChanges_unknownTablo_returns404() throws Exception {
        TableUpdateRequest updateRequest = new TableUpdateRequest("yeni_isim", null, null, null, null);

        mockMvc.perform(patch("/api/tables/{id}/changes", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_TABLE")));
    }
}
