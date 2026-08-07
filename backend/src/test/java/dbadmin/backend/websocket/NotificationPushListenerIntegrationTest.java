package dbadmin.backend.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.service.NotificationService;
import dbadmin.backend.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

/**
 * requirement-websocket-notifications.md Req-3.5: en kolay unutulabilecek/atlanabilecek detay —
 * push, event publish edildigi an degil, transaction COMMIT olduktan SONRA gitmeli. Bu test
 * {@code TableService}'i degil dogrudan {@code NotificationService.olustur} + gercek bir
 * {@code TransactionTemplate} kullanarak iki senaryoyu ayirir: commit eden transaction push
 * yapar, rollback olan hic yapmaz.
 */
class NotificationPushListenerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WebSocketSessionRegistry registry;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DataTable tabloWithOwner(Long ownerId, String name) {
        Schema schema = schemaRepository.findByNameIgnoreCase("push_test_sema")
                .orElseGet(() -> schemaService.createSchema("push_test_sema"));
        DataTable table = new DataTable(name);
        table.setSchema(schema);
        table.setCreatedByUserId(ownerId);
        return tableRepository.save(table);
    }

    @Test
    void olustur_transactionCommitOlunca_pushEdilir() throws Exception {
        Long ownerId = 5001L;
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        registry.add(ownerId, session);
        DataTable table = tabloWithOwner(ownerId, "push_commit_1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notificationService.create(table, 6001L, "tetikleyen1", NotificationType.COLUMN_ADDED, "test"));

        verify(session).sendMessage(any());
    }

    @Test
    void olustur_transactionRollbackOlunca_hicPushEdilmez() throws Exception {
        Long ownerId = 5002L;
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        registry.add(ownerId, session);
        DataTable table = tabloWithOwner(ownerId, "push_rollback_1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notificationService.create(table, 6002L, "tetikleyen2", NotificationType.COLUMN_ADDED, "test");
            status.setRollbackOnly();
        });

        verify(session, never()).sendMessage(any());
    }
}
