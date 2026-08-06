package dbadmin.backend.service;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.KolonRepository;
import dbadmin.backend.repository.KullaniciRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TabloRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saatlik rapor mailinin icerigini olusturma ve gonderme mantigi — bkz.
 * {@code requirement-scheduler-report.md}. Iki sorumluluk bilerek ayri metodlarda: biri DB'ye
 * bagimli ve senkron/test edilmesi kolay (Req-3.7), digeri sadece SMTP'ye bagimli ve async.
 * Hem {@code RaporScheduler} (saatlik) hem {@code RaporController} (manuel tetikleme, Req-2.4)
 * ayni servisi cagirir.
 */
@Service
public class RaporService {

    private static final Logger log = LoggerFactory.getLogger(RaporService.class);
    private static final DateTimeFormatter ZAMAN_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SchemaRepository schemaRepository;
    private final TabloRepository tabloRepository;
    private final KolonRepository kolonRepository;
    private final KullaniciRepository kullaniciRepository;
    private final AuditLogRepository auditLogRepository;
    private final JavaMailSender mailSender;
    private final String adminEmail;

    public RaporService(
            SchemaRepository schemaRepository,
            TabloRepository tabloRepository,
            KolonRepository kolonRepository,
            KullaniciRepository kullaniciRepository,
            AuditLogRepository auditLogRepository,
            JavaMailSender mailSender,
            @Value("${app.report.admin-email}") String adminEmail) {
        this.schemaRepository = schemaRepository;
        this.tabloRepository = tabloRepository;
        this.kolonRepository = kolonRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.auditLogRepository = auditLogRepository;
        this.mailSender = mailSender;
        this.adminEmail = adminEmail;
    }

    /**
     * Senkron: anlik goruntu (Req-2.2) + son 1 saatteki audit kayitlari (audit_log'dan, bkz.
     * requirement-audit-log.md). DB'ye bagimli oldugu icin ayri, kolayca test edilebilir bir
     * metod (Req-3.7) — mail gonderimiyle karistirmiyor.
     */
    @Transactional(readOnly = true)
    public String raporIcerigiOlustur() {
        long semaSayisi = schemaRepository.count();
        long tabloSayisi = tabloRepository.count();
        long kolonSayisi = kolonRepository.count();
        long kullaniciSayisi = kullaniciRepository.count();

        Instant birSaatOnce = Instant.now().minus(Duration.ofHours(1));
        List<AuditLog> sonIslemler = auditLogRepository
                .ara(null, null, birSaatOnce, null,
                        Pageable.unpaged(Sort.by(Sort.Direction.ASC, "olusturulmaZamani")))
                .getContent();

        StringBuilder icerik = new StringBuilder();
        icerik.append("DBAdmin Saatlik Rapor\n");
        icerik.append("======================\n\n");
        icerik.append("Anlik Durum\n");
        icerik.append("-----------\n");
        icerik.append("Schema sayisi: ").append(semaSayisi).append('\n');
        icerik.append("Tablo sayisi: ").append(tabloSayisi).append('\n');
        icerik.append("Kolon sayisi: ").append(kolonSayisi).append('\n');
        icerik.append("Kullanici sayisi: ").append(kullaniciSayisi).append("\n\n");

        icerik.append("Son 1 Saatteki Islemler (").append(sonIslemler.size()).append(")\n");
        icerik.append("-----------------------------\n");
        if (sonIslemler.isEmpty()) {
            icerik.append("Son 1 saatte islem yapilmadi.\n");
        } else {
            for (AuditLog satir : sonIslemler) {
                icerik.append(ZAMAN_FORMAT.format(satir.getOlusturulmaZamani()))
                        .append(" | ").append(satir.getKullaniciAdi())
                        .append(" | ").append(satir.getIslemTipi())
                        .append(" | ").append(satir.getHedefTip()).append('#').append(satir.getHedefId())
                        .append(" | ").append(satir.getDetay())
                        .append('\n');
            }
        }
        return icerik.toString();
    }

    /**
     * Async: sadece SMTP'ye bagimli, mail atma adimi (Req-2.5) — scheduler/controller thread'i
     * mail sunucusunu beklerken kilitlenmesin diye {@code raporTaskExecutor} havuzunda calisir
     * (bkz. AsyncConfig). Fail-open (Req-3.2): hata firlatilmaz, sadece ERROR loglanir — Redis
     * cache'teki fail-open ile ayni felsefe, raporlama bir bagimlilik degil bir ek ozellik.
     */
    @Async("raporTaskExecutor")
    public void raporGonder(String icerik) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("app.report.admin-email bos, rapor maili gonderilmiyor");
            return;
        }
        try {
            SimpleMailMessage mesaj = new SimpleMailMessage();
            mesaj.setTo(adminEmail);
            mesaj.setSubject("DBAdmin Saatlik Rapor");
            mesaj.setText(icerik);
            mailSender.send(mesaj);
        } catch (MailException ex) {
            log.error("rapor maili gonderilemedi: {}", ex.getMessage(), ex);
        }
    }
}
