package dbadmin.backend.service;

import dbadmin.backend.entity.AuditLog;
import dbadmin.backend.repository.AuditLogRepository;
import dbadmin.backend.repository.ColumnRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.repository.UserRepository;
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
 * Hem {@code ReportScheduler} (saatlik) hem {@code ReportController} (manuel tetikleme, Req-2.4)
 * ayni servisi cagirir.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SchemaRepository schemaRepository;
    private final TableRepository tableRepository;
    private final ColumnRepository columnRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final JavaMailSender mailSender;
    private final String adminEmail;

    public ReportService(
            SchemaRepository schemaRepository,
            TableRepository tableRepository,
            ColumnRepository columnRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            JavaMailSender mailSender,
            @Value("${app.report.admin-email}") String adminEmail) {
        this.schemaRepository = schemaRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.userRepository = userRepository;
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
    public String buildReportContent() {
        long schemaCount = schemaRepository.count();
        long tableCount = tableRepository.count();
        long columnCount = columnRepository.count();
        long userCount = userRepository.count();

        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        List<AuditLog> recentOperations = auditLogRepository
                .search(null, null, null, oneHourAgo, null,
                        Pageable.unpaged(Sort.by(Sort.Direction.ASC, "createdAt")))
                .getContent();

        StringBuilder content = new StringBuilder();
        content.append("DBAdmin Saatlik Rapor\n");
        content.append("======================\n\n");
        content.append("Anlik Durum\n");
        content.append("-----------\n");
        content.append("Schema sayisi: ").append(schemaCount).append('\n');
        content.append("Tablo sayisi: ").append(tableCount).append('\n');
        content.append("Kolon sayisi: ").append(columnCount).append('\n');
        content.append("Kullanici sayisi: ").append(userCount).append("\n\n");

        content.append("Son 1 Saatteki Islemler (").append(recentOperations.size()).append(")\n");
        content.append("-----------------------------\n");
        if (recentOperations.isEmpty()) {
            content.append("Son 1 saatte islem yapilmadi.\n");
        } else {
            for (AuditLog row : recentOperations) {
                content.append(TIME_FORMAT.format(row.getCreatedAt()))
                        .append(" | ").append(row.getUsername())
                        .append(" | ").append(row.getOperationType())
                        .append(" | ").append(row.getTargetType()).append('#').append(row.getTargetId())
                        .append(" | ").append(row.getDetail())
                        .append('\n');
            }
        }
        return content.toString();
    }

    /**
     * Async: sadece SMTP'ye bagimli, mail atma adimi (Req-2.5) — scheduler/controller thread'i
     * mail sunucusunu beklerken kilitlenmesin diye {@code reportTaskExecutor} havuzunda calisir
     * (bkz. AsyncConfig). Fail-open (Req-3.2): hata firlatilmaz, sadece ERROR loglanir — Redis
     * cache'teki fail-open ile ayni felsefe, raporlama bir bagimlilik degil bir ek ozellik.
     */
    @Async("reportTaskExecutor")
    public void sendReport(String content) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("app.report.admin-email bos, rapor maili gonderilmiyor");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("DBAdmin Saatlik Rapor");
            message.setText(content);
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("rapor maili gonderilemedi: {}", ex.getMessage(), ex);
        }
    }
}
