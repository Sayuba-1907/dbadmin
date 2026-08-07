package dbadmin.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.entity.Schema;
import dbadmin.backend.entity.DataTable;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.repository.SchemaRepository;
import dbadmin.backend.repository.TableRepository;
import dbadmin.backend.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * requirement-websocket-notifications.md Req-3.2: sahibi olmayan (eski) tablolar, uygulama
 * aciliminda ilk ADMIN'e atanir. {@link TableOwnerBackfillRunner} zaten test context'i ayaga
 * kalkarken bir kez otomatik calisiyor (bos DB'de no-op) — burada bilerek elle tekrar
 * cagiriyoruz: once bir tabloyu kasitli olarak sahipsiz birakip (owner=null), sonra runner'in
 * onu doldurdugunu dogruluyoruz.
 */
class TableOwnerBackfillRunnerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TableOwnerBackfillRunner runner;

    @Autowired
    private TableRepository tableRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaRepository schemaRepository;

    private Schema ensureSchema(String name) {
        return schemaRepository.findByNameIgnoreCase(name).orElseGet(() -> schemaService.createSchema(name));
    }

    @Test
    void run_assignsOwnerlessTablesToFirstAdmin() {
        DataTable sahipsiz = new DataTable("backfill_sahipsiz");
        sahipsiz.setSchema(ensureSchema("backfill_test_sema"));
        Long id = tableRepository.save(sahipsiz).getId();

        runner.run(null);

        Long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        assertEquals(adminId, tableRepository.findById(id).orElseThrow().getCreatedByUserId());
    }

    @Test
    void run_doesNotOverwriteExistingOwner() {
        DataTable sahipli = new DataTable("backfill_sahipli");
        sahipli.setSchema(ensureSchema("backfill_test_sema2"));
        sahipli.setCreatedByUserId(-1L);
        Long id = tableRepository.save(sahipli).getId();

        runner.run(null);

        assertEquals(-1L, tableRepository.findById(id).orElseThrow().getCreatedByUserId());
    }
}
