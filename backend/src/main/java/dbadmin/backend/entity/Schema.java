package dbadmin.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Kullanicinin olusturdugu bir Postgres schema'sinin metadata karsiligi — {@link Tablo} ile ayni
 * mantik: bu class gercek Postgres schema'si degil, onu tanimlayan kayittir; gercek
 * {@code CREATE SCHEMA} islemini {@link dbadmin.backend.ddl.SchemaDdlExecutor} yapar.
 * <p>
 * {@code name} benzersizligi de {@link Tablo#name} ile ayni sebeple DB constraint'i degil,
 * uygulama katiminda ({@link dbadmin.backend.service.SchemaService#createSchema}) saglanir.
 */
@Entity
@Table(name = "sema")
public class Schema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected Schema() {
    }

    public Schema(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Schema other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
