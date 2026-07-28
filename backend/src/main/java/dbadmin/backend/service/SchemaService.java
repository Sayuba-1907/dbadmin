package dbadmin.backend.service;

import dbadmin.backend.ddl.SchemaDdlExecutor;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TabloRepository;
import dbadmin.backend.validation.NameValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link TabloService} ile ayni mantik: her metod metadata + gercek Postgres semasini birlikte degistirir. */
@Service
public class SchemaService {

    /**
     * Postgres'in kendi varsayilan schema'si. DBAdmin acisindan tamamen gorunmez: listelenmez,
     * id'siyle sorulursa 404 doner, bu isimle yeni schema olusturulamaz ve hicbir tablo buraya
     * kurulamaz/tasinamaz. Sebep: {@code public} altyapiya ait — uygulamanin kendi metadata
     * tablolari ({@code tablo}, {@code kolon}, {@code sema}, {@code tag}) ve ileride kurulacak
     * extension/paketler orada duruyor; web arayuzunden degistirilebilir olmasi (ozellikle
     * {@code DROP SCHEMA public CASCADE}) uygulamanin kendisini silerdi.
     * <p>
     * Normalde {@code sema} tablosunda bu isimde bir satir hic bulunmaz. Buradaki kontroller
     * eski kurulumlar icin savunma katmani: bir sekilde boyle bir satir kalmissa da API onu
     * yokmus gibi gosterir.
     */
    public static final String RESERVED_SCHEMA_NAME = "public";

    private final SchemaRepository schemaRepository;
    private final TabloRepository tabloRepository;
    private final SchemaDdlExecutor ddlExecutor;
    private final Counter schemasCreatedCounter;
    private final Counter schemasDeletedCounter;

    public SchemaService(SchemaRepository schemaRepository, TabloRepository tabloRepository,
            SchemaDdlExecutor ddlExecutor, MeterRegistry meterRegistry) {
        this.schemaRepository = schemaRepository;
        this.tabloRepository = tabloRepository;
        this.ddlExecutor = ddlExecutor;
        // "creations"/"deletions" kullaniyoruz, "created" degil — bkz. TabloService'teki ayni
        // isimlendirmedeki not (Prometheus'ta "_created" rezerve bir sonek, Micrometer siliyor).
        this.schemasCreatedCounter = meterRegistry.counter("dbadmin.schemas.creations");
        this.schemasDeletedCounter = meterRegistry.counter("dbadmin.schemas.deletions");
        meterRegistry.gauge("dbadmin.schemas.active", schemaRepository, SchemaRepository::count);
    }

    /** {@link #RESERVED_SCHEMA_NAME} ("public") disari hic sizmaz — bkz. o sabitteki aciklama. */
    public static boolean isHidden(Schema schema) {
        return schema == null || RESERVED_SCHEMA_NAME.equalsIgnoreCase(schema.getName());
    }

    @Transactional(readOnly = true)
    public List<Schema> listSchemalar() {
        return schemaRepository.findAll().stream()
                .filter(schema -> !isHidden(schema))
                .toList();
    }

    /**
     * Gizli schema'lar icin bilerek {@link NotFoundException} firlatiyoruz, "yasak" anlaminda bir
     * hata degil: API yuzeyinde "public" diye bir schema yok, dolayisiyla onun id'siyle yapilan
     * GET/PATCH/DELETE de tanimsiz bir id'ye yapilmis sayilir.
     */
    @Transactional(readOnly = true)
    public Schema getSchema(Long id) {
        return schemaRepository.findById(id)
                .filter(schema -> !isHidden(schema))
                .orElseThrow(() -> new NotFoundException(
                        "NOT_FOUND_SCHEMA", "schema not found: " + id, Map.of("id", String.valueOf(id))));
    }

    @Transactional
    public Schema createSchema(String name) {
        NameValidator.validate("schema name", "VALIDATION_INVALID_SCHEMA_NAME", name);
        if (name.equalsIgnoreCase(RESERVED_SCHEMA_NAME)) {
            throw new ValidationException(
                    "VALIDATION_RESERVED_SCHEMA_NAME",
                    "'" + RESERVED_SCHEMA_NAME + "' is a reserved schema name and cannot be used",
                    Map.of("name", name));
        }
        if (schemaRepository.existsByName(name)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_SCHEMA_NAME",
                    "a schema named '" + name + "' already exists",
                    Map.of("name", name));
        }

        Schema saved = schemaRepository.save(new Schema(name));
        ddlExecutor.createSchema(saved.getName());
        schemasCreatedCounter.increment();
        return saved;
    }

    /**
     * "public" burada iki yerde reddedilir: kaynak olarak {@link #getSchema} zaten 404 verir
     * (gizli), hedef isim olarak da asagidaki kontrol engeller — yoksa var olan bir schema
     * "public" adini alarak gizli hale gelir ve icindeki tablolar erisilemez olurdu.
     */
    @Transactional
    public Schema renameSchema(Long id, String newName) {
        Schema schema = getSchema(id);
        NameValidator.validate("schema name", "VALIDATION_INVALID_SCHEMA_NAME", newName);
        if (newName.equalsIgnoreCase(RESERVED_SCHEMA_NAME)) {
            throw new ValidationException(
                    "VALIDATION_RESERVED_SCHEMA_NAME",
                    "'" + RESERVED_SCHEMA_NAME + "' is a reserved schema name and cannot be used",
                    Map.of("name", newName));
        }
        if (schema.getName().equals(newName)) {
            // Postgres "ALTER SCHEMA x RENAME TO x" ifadesini "schema already exists" diye
            // reddediyor — yeni isim eskiyle ayniysa DDL'e hic gitmiyoruz.
            return schema;
        }
        if (schemaRepository.existsByName(newName)) {
            throw new ConflictException(
                    "CONFLICT_DUPLICATE_SCHEMA_NAME",
                    "a schema named '" + newName + "' already exists",
                    Map.of("name", newName));
        }
        String oldName = schema.getName();
        schema.setName(newName);
        ddlExecutor.renameSchema(oldName, newName);
        return schema;
    }

    /**
     * Once bu schema'nin altindaki Tablo metadata satirlarini siliyoruz (Kolon'lar da
     * {@link Tablo}'daki cascade+orphanRemoval sayesinde otomatik gider), sonra Schema satirini
     * ve gercek Postgres schema'sini. Sira onemli: {@code ddlExecutor.dropSchema} zaten
     * {@code CASCADE} ile gercek tablolari fiziksel siliyor; metadata'yi da ayni yonde
     * temizlemezsek DBAdmin arayuzunde artik var olmayan "hayalet" tablolar gorunurdu.
     */
    @Transactional
    public void deleteSchema(Long id) {
        // getSchema "public" icin 404 verir; buraya asla "DROP SCHEMA public CASCADE" gelemez.
        Schema schema = getSchema(id);
        String name = schema.getName();
        List<Tablo> tablolarInSchema = tabloRepository.findBySchemaIdOrderByNameAsc(schema.getId());
        tabloRepository.deleteAll(tablolarInSchema);
        schemaRepository.delete(schema);
        ddlExecutor.dropSchema(name);
        schemasDeletedCounter.increment();
    }
}
