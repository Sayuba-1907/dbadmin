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
        name = "kolon",
        // tablo_id is looked up on every "list this table's columns" call,
        // so it is indexed; (tablo_id, name) is unique so a column name
        // only has to be unique within its own table, not globally.
        //tablo ıd ye göre araama yaptı kolona göe yapmamasının sebebi performans.
        indexes = @Index(name = "idx_kolon_tablo_id", columnList = "tablo_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"tablo_id", "name"})
        //aynı tablo içinde tablo ıd ve name kontrolu her birinden 1er tane olmasına bakıyor.

)
public class Kolon {

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
    // and disappears when the table does (see Tablo's cascade/orphanRemoval).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tablo_id", nullable = false)
    private Tablo tablo;

    // Reference, not composition: many columns may share a tag, and the
    // tag must survive column/table deletion. No cascade here on purpose.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    protected Kolon() {
    }

    public Kolon(String name, String type, Tablo tablo) {
        this.name = name;
        this.type = type;
        this.tablo = tablo;
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

    public Tablo getTablo() {
        return tablo;
    }

    void setTablo(Tablo tablo) {
        this.tablo = tablo;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Kolon other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
