package dbadmin.backend.repository;

import dbadmin.backend.entity.Tablo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TabloRepository extends JpaRepository<Tablo, Long> {

    Optional<Tablo> findByName(String name);
    //aynı isimde tablo var mı kontrlü.
    boolean existsByName(String name);
}
