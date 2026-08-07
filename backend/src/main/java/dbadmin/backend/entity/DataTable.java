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
 * {@code CREATE TABLE} islemini {@link dbadmin.backend.ddl.TableDdlExecutor} yapar. Sinif adi
 * bilerek {@code Table} degil {@code DataTable}: {@code jakarta.persistence.Table} (asagidaki
 * {@code @Table} sinif-notasyonu) ile ayni isimde olurdu.
 * <p>
 * {@code name} benzersizligi burada bir DB constraint'i ile degil, uygulama katmaninda
 * ({@link dbadmin.backend.service.TableService#createTable}, {@code existsByName} kontrolu)
 * saglanir.
 */
@Entity
@Table(name = "tables")
public class DataTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // DB kolonu bilerek nullable birakildi (nullable=false yazmadik): Hibernate ddl-auto=update
    // var olan satirlari doldurmadan NOT NULL bir kolon acamiyor. "Her tablo bir schema'ya
    // ait olmali" kurali burada DB constraint'iyle degil, uygulama katmaninda
    // (TableService#createTable schemaId'yi zorunlu tutar) saglanir. Yine de schema'si null
    // kalmis eski bir satir varsa TableService onu gizli sayip API'de hic gostermez.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schema_id")
    private Schema schema;

    // Composition: a DataColumn cannot exist without its DataTable, so the parent
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
    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DataColumn> columns = new ArrayList<>();

    // Bilerek Hibernate'in otomatik @UpdateTimestamp'ine guvenmiyoruz: o sadece DataTable'in kendi
    // satirindaki bir alan (ad/schema) degisince tetiklenir, kolon eklenip/silinip/yeniden
    // adlandirilinca (ayri bir DataColumn satiri degistigi icin) tetiklenmez. Bunun yerine
    // TableService'teki her mutasyon metodu (rename/changeSchema/addColumn/deleteColumn/
    // renameColumn/changeColumnTag) islemin sonunda {@link #touch()} cagirir — boylece "en son
    // ne zaman degisti" tablonun kendisi VE kolonlari icin de dogru sonucu verir. DB kolonu
    // bilerek nullable: Hibernate ddl-auto=update var olan satirlari doldurmadan NOT NULL bir
    // kolon acamiyor (bkz. yukaridaki schema alanindaki ayni not).
    private Instant updatedAt;

    // Ayni sebeple (ddl-auto=update mevcut satirlari doldurmadan NOT NULL kolon acamiyor)
    // nullable birakildi: yeni tablolar TableService#createTable icinde aktif kullanicidan
    // doldurulur, eski satirlar ise uygulama aciliminda TableOwnerBackfillRunner tarafindan
    // ilk ADMIN'e atanir (bkz. requirement-websocket-notifications.md Req-3.2).
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /** Parametresiz constructor JPA/Hibernate'in nesneyi reflection ile olusturabilmesi icin zorunlu; sen bunu hic cagirmazsin. */
    protected DataTable() {
    }

    public DataTable(String name) {
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

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    /** Tabloda ya da kolonlarindan birinde bir sey degisince TableService bunu cagirir — bkz. {@link #updatedAt}. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public List<DataColumn> getColumns() {
        return columns;
    }

    /**
     * Iliskinin iki tarafini da (DataTable->DataColumn listesi ve DataColumn->DataTable referansi) ayni anda
     * gunceller. Sadece {@code columns.add(column)} yapsaydik, DataColumn tarafindaki
     * {@code table} alani null kalir ve DB'ye kaydedilirken hata/tutarsizlik olurdu.
     */
    public void addColumn(DataColumn column) {
        columns.add(column);
        column.setTable(this);
    }

    /** addColumn'un tersi: listeden cikarir ve DataColumn'un tablo referansini da temizler. */
    public void removeColumn(DataColumn column) {
        columns.remove(column);
        column.setTable(null);
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
        if (!(o instanceof DataTable other)) {
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
