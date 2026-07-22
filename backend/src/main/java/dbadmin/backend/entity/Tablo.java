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
    // bir tablonun birden fazla kolonu olabileceğini söylüyor.
    //Cascade : bir tablo silersek kolonları da tabloyla beraber silinmesidir.
    //orphane Removal:Eğer bir tablodan sadece bir kolonu çıkarırsak o kolon yetim kalır ve hibernate ile bu kolon veri tabanından
    //otomatik silinir.
    // hibernate : javadan veri tabanına kodları çevirmeye yarar.
    //jpa : kuralları belirliyor nasıl yapılması gerktiğini söylüyor ama uygulayamıyor uygulama hibernate ile oluyor.
    //fetch type : lazy=>sadece tabloyu getirir kolonları  getirmek istediğimizde tablo.getkolon() diye çağırman gerekir.
    //eager=> tüm alt verileri tek seferde getirir.
    //n+1 problemi açısından lazy de  önce tabloyu getirip (+1) ardından n kezz sorgu atarız eagerda bunu tek seferde yapmıs oluruz.
    // join fetch=> n+1 in çözümü 1 seferde tüm her şeyi açarız.
    //@Transient:anlık hesaplama ya da baska durumlar için kullanılır. veritabanına eklenmez sadece java nesnesinde yasar.
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
