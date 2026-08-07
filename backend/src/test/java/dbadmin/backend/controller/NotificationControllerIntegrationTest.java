package dbadmin.backend.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.service.NotificationService;
import dbadmin.backend.service.SchemaService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * requirement-websocket-notifications.md Req-2.6/Req-2.7/Req-3.6: {@code NotificationController}'in
 * HTTP davranisi. Yetki kurali (rol kisiti olmamasi) ayrica {@code SecurityRulesIntegrationTest}'te
 * sinaniyor; burada odak ucun kendi davranisi (sadece kendi bildirimlerini gormek/isaretlemek).
 */
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private TableRepository tableRepository;

    private Long adminId() {
        return userRepository.findByUsername("admin").orElseThrow().getId();
    }

    private DataTable tabloWithOwner(Long ownerId, String name) {
        Schema schema = schemaRepository.findByNameIgnoreCase("bildirim_controller_test_sema")
                .orElseGet(() -> schemaService.createSchema("bildirim_controller_test_sema"));
        DataTable table = new DataTable(name);
        table.setSchema(schema);
        table.setCreatedByUserId(ownerId);
        return tableRepository.save(table);
    }

    @Test
    void okunmamisSayisi_kendiBildirimSayisiniDoner() throws Exception {
        Long ownerId = adminId();
        DataTable table = tabloWithOwner(ownerId, "controller_test_1");
        notificationService.create(table, 8001L, "editorX", NotificationType.COLUMN_ADDED, "test mesaji");

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void listele_kendiBildirimini_iceriktedir() throws Exception {
        Long ownerId = adminId();
        DataTable table = tabloWithOwner(ownerId, "controller_test_2");
        notificationService.create(table, 8002L, "editorY", NotificationType.COLUMN_DELETED, "column silindi mesaji");

        mockMvc.perform(get("/api/notifications").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.tableId == " + table.getId() + ")]").exists());
    }

    @Test
    void okunduIsaretle_kendiBildirimini_204DonerVeSayaciDusurur() throws Exception {
        Long ownerId = adminId();
        DataTable table = tabloWithOwner(ownerId, "controller_test_3");
        notificationService.create(table, 8003L, "editorZ", NotificationType.COLUMN_ADDED, "test");
        Long bildirimId = notificationService.list(ownerId, Pageable.unpaged())
                .getContent().stream()
                .filter(b -> b.getTableId().equals(table.getId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/notifications/" + bildirimId + "/read"))
                .andExpect(status().isNoContent());

        boolean okundu = notificationService.list(ownerId, Pageable.unpaged())
                .getContent().stream()
                .filter(b -> b.getId().equals(bildirimId))
                .findFirst().orElseThrow().isRead();
        Assertions.assertTrue(okundu);
    }

    @Test
    void okunduIsaretle_baskasininBildirimi_404Doner() throws Exception {
        DataTable table = tabloWithOwner(9999L, "controller_test_4");
        notificationService.create(table, 8004L, "editorW", NotificationType.COLUMN_ADDED, "test");
        Long baskasininBildirimId = notificationService.list(9999L, Pageable.unpaged())
                .getContent().stream()
                .filter(b -> b.getTableId().equals(table.getId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(patch("/api/notifications/" + baskasininBildirimId + "/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND_NOTIFICATION")));
    }

    @Test
    void tumunuOkunduIsaretle_204Doner() throws Exception {
        mockMvc.perform(patch("/api/notifications/read"))
                .andExpect(status().isNoContent());
    }
}
