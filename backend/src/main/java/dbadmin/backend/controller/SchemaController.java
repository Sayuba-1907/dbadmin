package dbadmin.backend.controller;

import dbadmin.backend.dto.CreateSchemaRequest;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.SchemaResponse;
import dbadmin.backend.dto.TabloResponse;
import dbadmin.backend.service.SchemaService;
import dbadmin.backend.service.TabloService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final TabloService tabloService;

    public SchemaController(SchemaService schemaService, TabloService tabloService) {
        this.schemaService = schemaService;
        this.tabloService = tabloService;
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

    /** GET /api/schemalar/{id}/tablolar — sidebar'daki schema -> tablo hiyerarsisi icin, bir schema'nin altindaki tablolar. Schema yoksa 404. */
    @GetMapping("/{id}/tablolar")
    public List<TabloResponse> listTablolar(@PathVariable Long id) {
        schemaService.getSchema(id);
        return tabloService.listTablolarBySchema(id).stream()
                .map(TabloResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SchemaResponse create(@RequestBody CreateSchemaRequest request) {
        return SchemaResponse.from(schemaService.createSchema(request.name()));
    }

    /** PATCH /api/schemalar/{id} — sadece ismi degistirir; "public" reddedilir (bkz. SchemaService.renameSchema). */
    @PatchMapping("/{id}")
    public SchemaResponse rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return SchemaResponse.from(schemaService.renameSchema(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        schemaService.deleteSchema(id);
    }
}
