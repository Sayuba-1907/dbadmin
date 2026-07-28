package dbadmin.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Kullanicinin olusturdugu bir "tablo"nun metadata karsiligi (entity = DB satirinin Java karsiligi).
 * Bu class'in kendisi gercek Postgres tablosu degil, onu tanimlayan kayittir; gercek
 * {@code CREATE TABLE} islemini {@link dbadmin.backend.ddl.TableDdlExecutor} yapar.
 * <p>
 * {@code name} benzersizligi burada bir DB constraint'i ile degil, uygulama katmaninda
 * ({@link dbadmin.backend.service.TabloService#createTablo}, {@code existsByName} kontrolu)
 * saglanir.
 */
@Entity
@Table(name = "tablo")
public class Tablo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // DB kolonu bilerek nullable birakildi (nullable=false yazmadik): Hibernate ddl-auto=update
    // var olan satirlari doldurmadan NOT NULL bir kolon acamiyor. "Her tablo bir schema'ya
    // ait olmali" kurali burada DB constraint'iyle degil, uygulama katmaninda
    // (TabloService#createTablo schemaId'yi zorunlu tutar) saglanir. Yine de schema'si null
    // kalmis eski bir satir varsa TabloService onu gizli sayip API'de hic gostermez.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_id")
    private Schema schema;

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

    // Bilerek Hibernate'in otomatik @UpdateTimestamp'ine guvenmiyoruz: o sadece Tablo'nun kendi
    // satirindaki bir alan (ad/schema) degisince tetiklenir, kolon eklenip/silinip/yeniden
    // adlandirilinca (ayri bir Kolon satiri degistigi icin) tetiklenmez. Bunun yerine
    // TabloService'teki her mutasyon metodu (rename/changeSchema/addKolon/deleteKolon/
    // renameKolon/changeKolonTag) islemin sonunda {@link #touch()} cagirir — boylece "en son
    // ne zaman degisti" tablonun kendisi VE kolonlari icin de dogru sonucu verir. DB kolonu
    // bilerek nullable: Hibernate ddl-auto=update var olan satirlari doldurmadan NOT NULL bir
    // kolon acamiyor (bkz. yukaridaki schema alanindaki ayni not).
    private Instant updatedAt;

    /** Parametresiz constructor JPA/Hibernate'in nesneyi reflection ile olusturabilmesi icin zorunlu; sen bunu hic cagirmazsin. */
    protected Tablo() {
    }

    public Tablo(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
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

    public Schema getSchema() {
        return schema;
    }

    public void setSchema(Schema schema) {
        this.schema = schema;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Tabloda ya da kolonlarindan birinde bir sey degisince TabloService bunu cagirir — bkz. {@link #updatedAt}. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public List<Kolon> getKolonlar() {
        return kolonlar;
    }

    /**
     * Iliskinin iki tarafini da (Tablo->Kolon listesi ve Kolon->Tablo referansi) ayni anda
     * gunceller. Sadece {@code kolonlar.add(kolon)} yapsaydik, Kolon tarafindaki
     * {@code tablo} alani null kalir ve DB'ye kaydedilirken hata/tutarsizlik olurdu.
     */
    public void addKolon(Kolon kolon) {
        kolonlar.add(kolon);
        kolon.setTablo(this);
    }

    /** addKolon'un tersi: listeden cikarir ve Kolon'un tablo referansini da temizler. */
    public void removeKolon(Kolon kolon) {
        kolonlar.remove(kolon);
        kolon.setTablo(null);
    }

    /**
     * JPA entity'lerde equals/hashCode klasik tuzagi: sadece {@code id} uzerinden kiyaslariz,
     * cunku henuz DB'ye kaydedilmemis (id = null) iki nesne asla esit sayilmamali, ve
     * Hibernate proxy'leri yuzunden getClass()/instanceof karisikligi olabilir.
     */
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

    /** Sabit bir hashCode donuyoruz: id zamanla degistigi icin (once null, sonra DB'nin verdigi deger) id'yi hashCode'a katmak, nesne bir Set/Map'teyken "kaybolmasina" yol acabilirdi. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
