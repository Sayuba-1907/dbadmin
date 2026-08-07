package dbadmin.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "columns",
        // table_id is looked up on every "list this table's columns" call,
        // so it is indexed; (table_id, name) is unique so a column name
        // only has to be unique within its own table, not globally.
        indexes = @Index(name = "idx_column_table_id", columnList = "table_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"table_id", "name"})
)
/**
 * Bir tablonun tek bir kolonunu temsil eden metadata kaydi. Sinif adi bilerek {@code Column}
 * degil {@code DataColumn}: {@code jakarta.persistence.Column} (yukaridaki {@code @Column}
 * alan-notasyonu) ile ayni isimde olurdu, ikisi de bu dosyada kullanilir.
 * <p>
 * {@code type} alani sabit bir whitelist'ten gelir (numeric/text/datetime/boolean, kontrolu
 * {@link dbadmin.backend.service.TableService}'te), ve olusturulduktan sonra degistirilemez
 * ({@code updatable = false}) — bir kolonun gercek DB tipini sonradan degistirmek migration
 * gerektirir, bu proje kapsaminda yok.
 */
public class DataColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Whitelist (numeric/text/datetime/boolean) is enforced in the service
    // layer, not here: this column stores whatever value the app already
    // validated, and drives the real CREATE TABLE's PostgreSQL type.
    @Column(nullable = false, updatable = false)
    private String type;

    // Reference, not composition: a column belongs to exactly one table,
    // and disappears when the table does (see DataTable's cascade/orphanRemoval).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private DataTable table;

    // Reference, not composition: many columns may share a tag, and the
    // tag must survive column/table deletion. No cascade here on purpose.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    // Bu alan gercek tablonun PRIMARY KEY'ini belirler: true olan kolonlarin tamami tek bir
    // Postgres PRIMARY KEY constraint'ini olusturur (tek kolon -> basit PK, birden fazla ->
    // composite PK: PRIMARY KEY (col1, col2)). Hicbiri true degilse tablo PK'siz kurulur.
    // Otomatik eklenen bir "id" kolonu yoktur — tabloda sadece kullanicinin tanimladigi
    // kolonlar bulunur.
    @Column(nullable = false)
    private boolean primaryKey;

    /** JPA/Hibernate'in reflection ile nesne olusturabilmesi icin zorunlu parametresiz constructor. */
    protected DataColumn() {
    }

    public DataColumn(String name, String type, DataTable table) {
        this.name = name;
        this.type = type;
        this.table = table;
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

    public String getType() {
        return type;
    }

    public DataTable getTable() {
        return table;
    }

    /** Package-private: sadece DataTable.addColumn/removeColumn cagirsin diye disariya kapali (public degil). */
    void setTable(DataTable table) {
        this.table = table;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    /** Sadece id'ye gore esitlik: kaydedilmemis (id=null) nesneler asla birbirine esit sayilmaz. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataColumn other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    /** Sabit deger: id degisebildigi (once null, sonra atanan deger) icin id'yi hashCode'a katmiyoruz. */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
