package dbadmin.backend.config;

import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.Tablo;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TabloRepository;
import dbadmin.backend.service.SchemaService;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uygulama her ayaga kalktiginda calisir: (1) "public" schema'yi temsil eden bir {@link Schema}
 * satirinin var oldugundan emin olur — bu, kullanicinin {@code createSchema} ile tekrar
 * yaratamayacagi (reddedilir), sistemin kendi olusturdugu tek satirdir; (2) henuz hicbir
 * schema'ya baglanmamis eski {@link Tablo} satirlarini bu satira baglar.
 * <p>
 * Boylece "her tablo bir schema'ya ait olmali" kurali, {@code Tablo.schema} DB kolonu nullable
 * olsa bile (bkz. o alandaki not) her zaman saglanmis olur.
 */
@Component
public class SchemaBootstrapRunner implements ApplicationRunner {

    private final SchemaRepository schemaRepository;
    private final TabloRepository tabloRepository;

    public SchemaBootstrapRunner(SchemaRepository schemaRepository, TabloRepository tabloRepository) {
        this.schemaRepository = schemaRepository;
        this.tabloRepository = tabloRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Schema publicSchema = schemaRepository.findByNameIgnoreCase(SchemaService.RESERVED_SCHEMA_NAME)
                .orElseGet(() -> schemaRepository.save(new Schema(SchemaService.RESERVED_SCHEMA_NAME)));

        List<Tablo> orphanTablolar = tabloRepository.findBySchemaIsNull();
        orphanTablolar.forEach(tablo -> tablo.setSchema(publicSchema));
        tabloRepository.saveAll(orphanTablolar);
    }
}
