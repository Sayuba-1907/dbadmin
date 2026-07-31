package dbadmin.backend.repository;

import dbadmin.backend.entity.Schema;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link TabloRepository} ile ayni mantik — bkz. oradaki aciklama. */
public interface SchemaRepository extends JpaRepository<Schema, Long> {

    boolean existsByName(String name);

    /** Testlerin "public" satiri gercekten yok mu" diye kontrol edebilmesi icin. */
    Optional<Schema> findByNameIgnoreCase(String name);
}
