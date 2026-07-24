package dbadmin.backend.repository;

import dbadmin.backend.entity.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository — method-name'den otomatik sorgu uretilir, implementasyon yazilmaz. */
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    /** Tam satiri cekmeden sadece var/yok bilgisini doner — uniqueness kontrolu icin daha ucuz. */
    boolean existsByName(String name);
}
