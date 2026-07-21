package dbadmin.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tablo", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Tablo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Composition: a Kolon cannot exist without its Tablo, so the parent
    // owns the lifecycle of its columns (cascade + orphanRemoval).
    @OneToMany(mappedBy = "tablo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Kolon> kolonlar = new ArrayList<>();

    protected Tablo() {
    }

    public Tablo(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Kolon> getKolonlar() {
        return kolonlar;
    }

    public void addKolon(Kolon kolon) {
        kolonlar.add(kolon);
        kolon.setTablo(this);
    }

    public void removeKolon(Kolon kolon) {
        kolonlar.remove(kolon);
        kolon.setTablo(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tablo other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
