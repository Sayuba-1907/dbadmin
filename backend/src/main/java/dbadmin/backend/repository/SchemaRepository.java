package dbadmin.backend.repository;

import dbadmin.backend.entity.Schema;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link TabloRepository} ile ayni mantik — bkz. oradaki aciklama. */
public interface SchemaRepository extends JpaRepository<Schema, Long> {

    boolean existsByName(String name);

    /** SchemaBootstrapRunner'in "public" satirini case-insensitive bulmasi icin. */
    Optional<Schema> findByNameIgnoreCase(String name);
}
