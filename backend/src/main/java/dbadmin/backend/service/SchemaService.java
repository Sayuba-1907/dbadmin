package dbadmin.backend.service;

import dbadmin.backend.ddl.SchemaDdlExecutor;
import dbadmin.backend.dto.SchemaResponseDTO;
import dbadmin.backend.dto.TableSummaryDTO;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dbadmin.backend.dto.SchemaResponse;

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
    public List<SchemaResponse> listSchemalar() {
        return schemaRepository.findAll().stream()
                .filter(schema -> !isHidden(schema))
                .map(this::toResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public List<SchemaResponseDTO>getSchemaListV2() {
        List<Tablo> tableList = tabloRepository.findAllByOrderByNameAsc();
        Map<Long, SchemaResponseDTO> schemaIdSchemaResponseMap = new HashMap<>();

        for (Tablo table : tableList) {
            TableSummaryDTO tableSummaryDTO = new TableSummaryDTO(table.getId(), table.getName(),
                    table.getKolonlar().size(), table.getSchema().getId());
            if (schemaIdSchemaResponseMap.containsKey(table.getSchema().getId())) {
                schemaIdSchemaResponseMap.get(table.getSchema().getId()).addTableSummaryToList(tableSummaryDTO);
            } else {
                SchemaResponseDTO schemaResponseDTO = new SchemaResponseDTO(table.getSchema().getId(),
                        table.getSchema().getName());
                schemaResponseDTO.addTableSummaryToList(tableSummaryDTO);
                schemaIdSchemaResponseMap.put(schemaResponseDTO.getSchemaId(), schemaResponseDTO);
            }

        }
        return new ArrayList<>(schemaIdSchemaResponseMap.values());
    }

    @Transactional(readOnly = true)
    public List<SchemaResponseDTO>getSchemaList(){

        Map<Long, SchemaResponseDTO> schemaIdSchemaResponseMap = new HashMap<>();

        // Once TUM schema'lar icin (tablosu olmasa bile) bos bir DTO acilir — asagidaki
        // findAllSchemaTabloPairs sadece tablosu OLAN schema'lari uretir (inner join gibi
        // davranir), bu yuzden yeni olusturulup henuz tablo eklenmemis bir schema o listede
        // hic gorunmez ve olusturulmasaydi frontend'de kayboluyordu.
        schemaRepository.findAll().stream()
                .filter(schema -> !isHidden(schema))
                .forEach(schema -> schemaIdSchemaResponseMap.put(schema.getId(),
                        new SchemaResponseDTO(schema.getId(), schema.getName())));

        Map<Long, Long> tabloIdColumnCountMap = tabloRepository.countKolonlarGroupByTablo().stream()
                .collect(Collectors
                        .toMap(TabloRepository.TabloKolonCountProjection::getTabloId,
                                TabloRepository.TabloKolonCountProjection::getKolonSayisi));


        List<TabloRepository.SchemaTabloProjection> schemaTablePairs = tabloRepository.findAllSchemaTabloPairs();
        for (TabloRepository.SchemaTabloProjection schemaTablePair : schemaTablePairs) {
            // Hic kolonu olmayan bir tablo GROUP BY sonucunda hic satir uretmez (bkz.
            // countKolonlarGroupByTablo javadoc'u), yani map'te yer almaz — getOrDefault ile 0 kabul ediyoruz.
            TableSummaryDTO tableSummaryDTO = new TableSummaryDTO(schemaTablePair.getTabloId(), schemaTablePair.getTabloName(),
                    tabloIdColumnCountMap.getOrDefault(schemaTablePair.getTabloId(), 0L).intValue(),
                    schemaTablePair.getSchemaId());
            if(schemaIdSchemaResponseMap.containsKey(schemaTablePair.getSchemaId())){
                schemaIdSchemaResponseMap.get(schemaTablePair.getSchemaId()).addTableSummaryToList(tableSummaryDTO);
            }else{
                SchemaResponseDTO schemaResponseDTO = new SchemaResponseDTO(schemaTablePair.getSchemaId(),
                        schemaTablePair.getSchemaName());
                schemaIdSchemaResponseMap.put(schemaTablePair.getSchemaId(),schemaResponseDTO);
                schemaResponseDTO.addTableSummaryToList(tableSummaryDTO);
                schemaIdSchemaResponseMap.put(schemaResponseDTO.getSchemaId(),schemaResponseDTO);
            }
        }
        return new ArrayList<>(schemaIdSchemaResponseMap.values());

    }

    /**
     * {@link Schema} entity'sini disari donen DTO'ya cevirir, tablo sayisini da hesaplayip ekler.
     * Tek yerde toplanmasinin sebebi: bu cevirme islemi listSchemalar/getSchema/createSchema/
     * renameSchema'nin hepsinde gerekiyor — hepsi ayni sorguyu (countBySchemaId) tekrar tekrar
     * yazmak yerine buraya geliyor.
     */
    public SchemaResponse toResponse(Schema schema) {
        long tabloSayisi = tabloRepository.countBySchemaId(schema.getId());
        return SchemaResponse.from(schema, tabloSayisi);
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
