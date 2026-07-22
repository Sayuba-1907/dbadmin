package dbadmin.backend.repository;

import dbadmin.backend.entity.Kolon;
import dbadmin.backend.entity.Tablo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KolonRepository extends JpaRepository<Kolon, Long> {

    boolean existsByTabloAndName(Tablo tablo, String name);
    //aynı tablo içerisideki yeni bir kolonda aynı ada sahiplik durumu var mı kontrolünü yaparız.
}