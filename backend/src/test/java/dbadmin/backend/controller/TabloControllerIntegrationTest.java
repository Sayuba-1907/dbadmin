package dbadmin.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.CreateKolonRequest;
import dbadmin.backend.dto.CreateTabloRequest;
import dbadmin.backend.dto.RenameRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

// Automates the same manual curl checks run earlier: every use case must
// come back with the HTTP status code the spec requires (201/200/400/404/
// 409/204), because the frontend colours its notifications off of that
// status. A regression here (e.g. a 500 instead of a 409) fails the build.
@AutoConfigureMockMvc
class TabloControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void create_returns201() throws Exception {
        CreateTabloRequest request = new CreateTabloRequest("ders1",
                List.of(new CreateKolonRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tablolar").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("ders1")));
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        CreateTabloRequest request = new CreateTabloRequest("ders2",
                List.of(new CreateKolonRequest("ad", "text", null)));
        mockMvc.perform(post("/api/tablolar").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/tablolar").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void create_invalidName_returns400() throws Exception {
        CreateTabloRequest request = new CreateTabloRequest("Buyuk",
                List.of(new CreateKolonRequest("ad", "text", null)));

        mockMvc.perform(post("/api/tablolar").contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/tablolar/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void list_returns200() throws Exception {
        mockMvc.perform(get("/api/tablolar")).andExpect(status().isOk());
    }

    @Test
    void fullLifecycle_rename_addColumn_changeTag_deleteColumn_deleteTable() throws Exception {
        CreateTabloRequest createRequest = new CreateTabloRequest("ders3",
                List.of(new CreateKolonRequest("ad", "text", null)));
        String createResponse = mockMvc.perform(
                        post("/api/tablolar").contentType(MediaType.APPLICATION_JSON).content(json(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tabloId = objectMapper.readTree(createResponse).get("id").asLong();
        long kolonId = objectMapper.readTree(createResponse).get("kolonlar").get(0).get("id").asLong();

        mockMvc.perform(patch("/api/tablolar/{id}", tabloId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RenameRequest("ders3_yeni"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("ders3_yeni")));

        mockMvc.perform(post("/api/tablolar/{id}/kolonlar", tabloId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateKolonRequest("puan", "numeric", null))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/tablolar/{id}/kolonlar/{kolonId}/tag", tabloId, kolonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ChangeTagRequest(null))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/tablolar/{id}/kolonlar/{kolonId}", tabloId, kolonId))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tablolar/{id}", tabloId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tablolar/{id}", tabloId))
                .andExpect(status().isNotFound());
    }
}
