package dbadmin.backend.repository;

import dbadmin.backend.entity.Role;
import dbadmin.backend.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Girişte ve her JWT dogrulamasinda kullanilir — kullanici adi login'in dogal anahtaridir. */
    Optional<User> findByUsername(String username);

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin daha ucuz. */
    boolean existsByUsername(String username);

    /** Tablo sahipligi backfill'i (TableOwnerBackfillRunner) icin: ilk ADMIN'i id'ye gore secer, sonuc deterministik olsun diye. */
    Optional<User> findFirstByRoleOrderByIdAsc(Role role);
}
