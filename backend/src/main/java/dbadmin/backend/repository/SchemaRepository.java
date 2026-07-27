package dbadmin.backend.repository;

import dbadmin.backend.entity.Schema;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link TabloRepository} ile ayni mantik — bkz. oradaki aciklama. */
public interface SchemaRepository extends JpaRepository<Schema, Long> {

    boolean existsByName(String name);
}
