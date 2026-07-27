package dbadmin.backend.controller;

import dbadmin.backend.dto.CreateSchemaRequest;
import dbadmin.backend.dto.SchemaResponse;
import dbadmin.backend.service.SchemaService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Schema icin HTTP endpoint'leri — {@link TabloController} ile ayni mantik, bkz. oradaki aciklama. */
@RestController
@RequestMapping("/api/schemalar")
public class SchemaController {

    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping
    public List<SchemaResponse> list() {
        return schemaService.listSchemalar().stream()
                .map(SchemaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public SchemaResponse get(@PathVariable Long id) {
        return SchemaResponse.from(schemaService.getSchema(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaResponse create(@RequestBody CreateSchemaRequest request) {
        return SchemaResponse.from(schemaService.createSchema(request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schemaService.deleteSchema(id);
    }
}
