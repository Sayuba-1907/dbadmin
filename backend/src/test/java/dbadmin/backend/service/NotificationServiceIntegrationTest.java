package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Notification;
import dbadmin.backend.entity.NotificationType;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link NotificationService}'i {@code TableService}'ten bagimsiz, dogrudan test eder — bkz.
 * requirement-websocket-notifications.md Req-2.3/Req-3.3/Req-2.6/Req-2.7/Req-3.6.
 */
class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DataTable tabloWithOwner(Long ownerId, String name) {
        Schema schema = schemaRepository.findByNameIgnoreCase("bildirim_servis_test_sema")
                .orElseGet(() -> schemaService.createSchema("bildirim_servis_test_sema"));
        DataTable table = new DataTable(name);
        table.setSchema(schema);
        table.setCreatedByUserId(ownerId);
        return tableRepository.save(table);
    }

    @Test
    void olustur_tetikleyenSahipDegil_bildirimSatiriOlusturur() {
        DataTable table = tabloWithOwner(1001L, "bildirim_servis_1");

        notificationService.create(table, 2001L, "tetikleyen1", NotificationType.COLUMN_ADDED, "test mesaji");

        Page<Notification> sayfa = notificationService.list(1001L, Pageable.unpaged());
        assertEquals(1, sayfa.getTotalElements());
        Notification notification = sayfa.getContent().get(0);
        assertEquals(table.getId(), notification.getTableId());
        assertEquals(table.getName(), notification.getTableName());
        assertEquals("tetikleyen1", notification.getTriggeredByUsername());
        assertEquals(NotificationType.COLUMN_ADDED, notification.getType());
        assertFalse(notification.isRead());
    }

    // Req-3.3: sahip kendi degisikligi icin kendine notification almaz.
    @Test
    void olustur_tetikleyenSahibinKendisi_hicbirSeyOlusturmaz() {
        DataTable table = tabloWithOwner(1002L, "bildirim_servis_2");

        notificationService.create(table, 1002L, "sahip2", NotificationType.COLUMN_ADDED, "test");

        assertEquals(0, notificationService.unreadCount(1002L));
    }

    // Savunmaci durum: backfill'den once kalmis (owner=null) bir table — notification uretilemez, kime gidecegi belli degil.
    @Test
    void olustur_tablonunSahibiYok_hicbirSeyOlusturmaz() {
        DataTable table = tabloWithOwner(null, "bildirim_servis_3");

        notificationService.create(table, 3001L, "tetikleyen3", NotificationType.COLUMN_ADDED, "test");

        // "sahipsiz" oldugu icin hicbir recipientUserId altinda satir olusmamis olmali.
        assertEquals(0, notificationService.unreadCount(3001L));
    }

    @Test
    void okunduIsaretle_kendiBildirimini_okunduYapar() {
        DataTable table = tabloWithOwner(1003L, "bildirim_servis_4");
        notificationService.create(table, 2003L, "tetikleyen4", NotificationType.COLUMN_DELETED, "test");
        Long bildirimId = notificationService.list(1003L, Pageable.unpaged()).getContent().get(0).getId();

        notificationService.markAsRead(bildirimId, 1003L);

        assertEquals(0, notificationService.unreadCount(1003L));
    }

    // Req-3.6: baskasinin bildirimine erisim NotFoundException ile reddedilir (var/yok ayrimi sizdirilmaz).
    @Test
    void okunduIsaretle_baskasininBildirimi_notFound() {
        DataTable table = tabloWithOwner(1004L, "bildirim_servis_5");
        notificationService.create(table, 2004L, "tetikleyen5", NotificationType.COLUMN_DELETED, "test");
        Long bildirimId = notificationService.list(1004L, Pageable.unpaged()).getContent().get(0).getId();

        assertThrows(NotFoundException.class, () -> notificationService.markAsRead(bildirimId, 9999L));
    }

    /**
     * requirement-websocket-notifications.md Req-3.4'un fail-closed yarisi (plan Faz 6 Adim
     * 6.2): {@code Notification} satiri, audit log ile ayni ilkeyle cagiranin transaction'ina katilir
     * — bu transaction'da SONRADAN bir baska sey patlarsa (burada elle firlatilan bir
     * RuntimeException'la simule edildi), az once yazilmis GORUNEN Notification satiri da rollback
     * ile birlikte geri alinir. TableService'teki gercek mutasyon metotlarinin hicbirinde bu
     * senaryo dogal olarak olusmuyor ({@code bildir(...)} her zaman kendi DDL'i basardiktan sonra,
     * metodun EN SONUNDA cagriliyor) — o yuzden burada dogrudan {@code TransactionTemplate} ile
     * "ayni transaction'da sonradan bir hata" durumu elle kuruluyor (bkz. BildirimPushListenerIntegrationTest'teki
     * ayni yaklasim, orada AFTER_COMMIT push'u icin kullanildi).
     */
    @Test
    void olustur_ayniTransactionSonradanRollbackOlursa_bildirimSatiriDaGeriAlinir() {
        DataTable table = tabloWithOwner(1007L, "bildirim_servis_7");

        assertThrows(RuntimeException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    notificationService.create(table, 2007L, "tetikleyen7", NotificationType.COLUMN_ADDED, "test");
                    throw new RuntimeException("ayni transaction'da sonradan patlayan baska bir islem");
                }));

        assertEquals(0, notificationService.unreadCount(1007L));
    }

    @Test
    void tumunuOkunduIsaretle_sadeceKendiBildirimleriniEtkiler() {
        DataTable table = tabloWithOwner(1005L, "bildirim_servis_6");
        notificationService.create(table, 2005L, "a", NotificationType.COLUMN_ADDED, "1");
        notificationService.create(table, 2006L, "b", NotificationType.COLUMN_DELETED, "2");
        assertEquals(2, notificationService.unreadCount(1005L));

        notificationService.markAllAsRead(1005L);

        assertEquals(0, notificationService.unreadCount(1005L));
    }
}
