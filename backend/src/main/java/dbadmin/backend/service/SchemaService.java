package dbadmin.backend.service;

import dbadmin.backend.ddl.SchemaDdlExecutor;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.exception.ConflictException;
import dbadmin.backend.exception.NotFoundException;
import dbadmin.backend.exception.ValidationException;
import dbadmin.backend.repository.SchemaRepository;
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

    /** Postgres'in kendi varsayilan schema'si — bu isimde ikinci bir schema olusturulamaz. */
    private static final String RESERVED_SCHEMA_NAME = "public";

    private final SchemaRepository schemaRepository;
    private final SchemaDdlExecutor ddlExecutor;
    private final Counter schemasCreatedCounter;
    private final Counter schemasDeletedCounter;

    public SchemaService(SchemaRepository schemaRepository, SchemaDdlExecutor ddlExecutor, MeterRegistry meterRegistry) {
        this.schemaRepository = schemaRepository;
        this.ddlExecutor = ddlExecutor;
        // "creations"/"deletions" kullaniyoruz, "created" degil — bkz. TabloService'teki ayni
        // isimlendirmedeki not (Prometheus'ta "_created" rezerve bir sonek, Micrometer siliyor).
        this.schemasCreatedCounter = meterRegistry.counter("dbadmin.schemas.creations");
        this.schemasDeletedCounter = meterRegistry.counter("dbadmin.schemas.deletions");
        meterRegistry.gauge("dbadmin.schemas.active", schemaRepository, SchemaRepository::count);
    }

    @Transactional(readOnly = true)
    public List<Schema> listSchemalar() {
        return schemaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Schema getSchema(Long id) {
        return schemaRepository.findById(id)
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

    @Transactional
    public void deleteSchema(Long id) {
        Schema schema = getSchema(id);
        String name = schema.getName();
        schemaRepository.delete(schema);
        ddlExecutor.dropSchema(name);
        schemasDeletedCounter.increment();
    }
}
