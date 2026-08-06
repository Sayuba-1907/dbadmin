package dbadmin.backend.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.HedefTip;
import dbadmin.backend.entity.IslemTipi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * requirement-scheduler-report.md Faz 6: {@link RaporService#raporIcerigiOlustur} icin gercek
 * Postgres'e karsi (Req-3.7 — DB'ye bagimli kisim test edilebilir olmali), {@link
 * RaporService#raporGonder} icin ise projenin genel "gercek DB, mock yok" ilkesinden bilinerek
 * sapilip {@link JavaMailSender} mock'lanir (Req plan Adim 6.2 — SMTP dis bir sistem, her test
 * calistirmasinda gercek bir mail kutusuna mail dusurmek hem yanlis hem yavas).
 */
@WithMockUser(username = "admin", roles = "ADMIN")
@TestPropertySource(properties = "app.report.admin-email=admin@dbadmin-test.local")
class RaporServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RaporService raporService;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private TabloService tabloService;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void raporIcerigiOlustur_anlikSayilariVeSonIslemleriIcerir() {
        var schema = schemaService.createSchema("rapor_test_sema");
        tabloService.createTablo("rapor_test_tablo", schema.getId(),
                List.of(new KolonTanimi("ad", "text", null)));

        String icerik = raporService.raporIcerigiOlustur();

        assertTrue(icerik.contains("Schema sayisi:"));
        assertTrue(icerik.contains("Tablo sayisi:"));
        assertTrue(icerik.contains(IslemTipi.SCHEMA_OLUSTURULDU.name()));
        assertTrue(icerik.contains(IslemTipi.TABLO_OLUSTURULDU.name()));
        assertTrue(icerik.contains(HedefTip.SCHEMA.name()));
    }

    @Test
    void raporGonder_basarili_mailSenderCagrilir() {
        raporService.raporGonder("test icerigi");

        verify(mailSender, timeout(2000)).send(argThat((SimpleMailMessage mesaj) ->
                mesaj.getTo() != null
                        && mesaj.getTo()[0].equals("admin@dbadmin-test.local")
                        && "test icerigi".equals(mesaj.getText())));
    }

    /** Req-3.2 (fail-open): SMTP hata firlatsa bile cagiran taraf hicbir istisna gormemeli. */
    @Test
    void raporGonder_smtpHataVerirse_istisnaDisariSizmaz() {
        doThrow(new MailAuthenticationException("test: kimlik dogrulama basarisiz"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        raporService.raporGonder("test icerigi");

        verify(mailSender, timeout(2000)).send(any(SimpleMailMessage.class));
    }
}
