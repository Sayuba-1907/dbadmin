package dbadmin.backend.config;

import dbadmin.backend.entity.DataTable;
import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.User;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablo sahipligi ({@link DataTable#getCreatedByUserId}) alani sonradan eklendi; ondan onceki
 * satirlarda bu alan null'dir. Bu sinif her acilista o satirlari bulup ilk ADMIN'e atar
 * (bkz. requirement-websocket-notifications.md Req-3.2) — bilincli bir varsayim, o tabloyu
 * gercekte admin olusturmamis olabilir ama bildirim ozelligi (Faz 2) her tabloda bir sahip
 * bulunmasini gerektiriyor.
 *
 * <p>Idempotent: sadece {@code createdByUserId IS NULL} olan satirlari degistirir, zaten
 * doldurulmus bir satiri (yeni tablolar zaten {@code TableService#createTable} icinde doluyor)
 * bir daha ele almaz — {@link UserSeeder}'daki "count == 0 ise calis" ile ayni fikir,
 * burada kosul sadece satir bazinda.
 */
@Component
public class TableOwnerBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TableOwnerBackfillRunner.class);

    private final TableRepository tableRepository;
    private final UserRepository userRepository;

    public TableOwnerBackfillRunner(TableRepository tableRepository, UserRepository userRepository) {
        this.tableRepository = tableRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DataTable> ownerlessTables = tableRepository.findByCreatedByUserIdIsNull();
        if (ownerlessTables.isEmpty()) {
            return;
        }
        User firstAdmin = userRepository.findFirstByRoleOrderByIdAsc(Role.ADMIN)
                .orElse(null);
        if (firstAdmin == null) {
            // UserSeeder henuz calismamis / hic ADMIN yoksa: bir sonraki aciliste tekrar denenir.
            log.warn("Sahipsiz {} tablo var ama hic ADMIN bulunamadi, backfill atlandi.",
                    ownerlessTables.size());
            return;
        }
        for (DataTable table : ownerlessTables) {
            table.setCreatedByUserId(firstAdmin.getId());
        }
        tableRepository.saveAll(ownerlessTables);
        log.info("{} sahipsiz tablo ilk ADMIN'e ('{}') atandi.",
                ownerlessTables.size(), firstAdmin.getUsername());
    }
}
