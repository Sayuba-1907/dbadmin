package dbadmin.backend.websocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.config.RabbitConfig;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.service.NotificationService;
import dbadmin.backend.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketSession;

/**
 * requirement-websocket-notifications.md Req-3.5: en kolay unutulabilecek/atlanabilecek detay —
 * push, RabbitMQ'ya publish edildigi an degil, transaction COMMIT olduktan SONRA gitmeli. Bu test
 * {@code TableService}'i degil dogrudan {@code NotificationService.create} + gercek bir {@code
 * TransactionTemplate} kullanarak iki senaryoyu ayirir: commit eden transaction push yapar,
 * rollback olan hic yapmaz. Push artik gercek bir kuyruktan (Testcontainers RabbitMQ) asenkron
 * gectigi icin dogrulama {@code Mockito.timeout(...)} ile bekleyerek yapilir.
 */
class RabbitNotificationListenerIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private RabbitTemplate rabbitTemplate;

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

        verify(session, timeout(5000)).sendMessage(any());
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

        // Kisa bir bekleme penceresinde sendMessage hic cagrilmadigini dogrular — verify(never())
        // ani kontrol edip gecerdi, oysa burada dogrulanmasi gereken sey "asla gelmeyecegi", commit
        // olsaydi birkac yuz ms icinde gelecek olan push'un gerceklestigi zaman penceresi degil.
        Thread.sleep(2000);
        verify(session, never()).sendMessage(any());
    }

    /**
     * RabbitConfig'teki DLX/DLQ kablolamasini dogrudan dogrular: {@code RabbitNotificationListener}
     * deserialize edemeyecegi bir mesajla (NotificationMessage degil, duz bir String) karsilasinca
     * istisna firlatir, retry (application.properties'teki spring.rabbitmq.listener.simple.retry.*)
     * tukenince mesaj queue'nun x-dead-letter-exchange argumani sayesinde otomatik DLQ'ya duser.
     */
    @Test
    void islenemeyenMesaj_retryTukenince_dlqyaDuser() {
        rabbitTemplate.convertAndSend(
                RabbitConfig.NOTIFICATION_EXCHANGE, RabbitConfig.NOTIFICATION_ROUTING_KEY, "gecersiz-mesaj-govdesi");

        Object dlqMessage = rabbitTemplate.receiveAndConvert(RabbitConfig.NOTIFICATION_DLQ, 8000);

        assertNotNull(dlqMessage, "retry tukenince mesaj DLQ'da bulunmali");
        // Ana queue'da artik hicbir sey kalmamis olmali (DLX'e tasindi).
        assertNull(rabbitTemplate.receiveAndConvert(RabbitConfig.NOTIFICATION_QUEUE, 500));
    }
}
