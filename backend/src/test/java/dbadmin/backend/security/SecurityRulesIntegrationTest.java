package dbadmin.backend.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.CreateTagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Yetki kurallarinin ({@link SecurityConfig}) gercekten uygulandigini sinar — "kimisi update
 * edebilsin kimisi edemesin" maddesinin karsiligi.
 * <p>
 * Sinif seviyesinde bilerek {@code @WithMockUser} <b>yok</b>: her test kendi rolunu (ya da
 * rolsuzlugunu) tanimlar, cunku burada olculen sey ucun davranisi degil rolun etkisi.
 */
@AutoConfigureMockMvc
class SecurityRulesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- Kimliksiz istekler ---------------------------------------------------

    @Test
    void kimliksizIstek_korunanUcta_401DonerVeAyniHataSeklindeGelir() throws Exception {
        mockMvc.perform(get("/api/tablolar"))
                .andExpect(status().isUnauthorized())
                // Asil sinav bu: 401 Spring Security'nin filtre zincirinden geliyor, yani
                // GlobalExceptionHandler'a hic ugramiyor. Yine de code/message alanlari dolu
                // olmali, yoksa frontend'in cevirisi tam da giris hatasinda bos kalirdi.
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void kimliksizIstek_bozukTokenla_401Doner() throws Exception {
        mockMvc.perform(get("/api/tablolar").header("Authorization", "Bearer bu-token-uydurma"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_REQUIRED")));
    }

    @Test
    void hataMesajiAcceptLanguageBasligina_gore_cevrilir() throws Exception {
        mockMvc.perform(get("/api/tablolar").header("Accept-Language", "tr"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Bu işlem için giriş yapmalısınız")));
    }

    // --- VIEWER: okuyabilir, yazamaz -----------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = "VIEWER")
    void viewer_okuyabilir() throws Exception {
        mockMvc.perform(get("/api/tablolar")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "VIEWER")
    void viewer_yazamaz_403Doner() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTagRequest("viewer_denemesi"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "VIEWER")
    void viewer_kullaniciYonetimineGiremez() throws Exception {
        mockMvc.perform(get("/api/kullanicilar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "VIEWER")
    void viewer_auditLoglaraGiremez() throws Exception {
        mockMvc.perform(get("/api/audit-loglar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "VIEWER")
    void viewer_raporTetikleyemez() throws Exception {
        mockMvc.perform(post("/api/raporlar/gonder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    // --- EDITOR: yazabilir, kullanici yonetemez ------------------------------

    @Test
    @WithMockUser(username = "admin", roles = "EDITOR")
    void editor_yazabilir() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTagRequest("editor_etiketi"))))
                .andExpect(status().isCreated());
    }

    /**
     * Kurallarin sirasinin dogru oldugunu da sinar: {@code /api/kullanicilar/**} kurali genel
     * {@code GET /api/**} kuralindan once gelmezse, EDITOR bu ucu okuyabilir hale gelirdi.
     */
    @Test
    @WithMockUser(username = "admin", roles = "EDITOR")
    void editor_kullaniciYonetimineGiremez() throws Exception {
        mockMvc.perform(get("/api/kullanicilar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "EDITOR")
    void editor_auditLoglaraGiremez() throws Exception {
        mockMvc.perform(get("/api/audit-loglar"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "EDITOR")
    void editor_raporTetikleyemez() throws Exception {
        mockMvc.perform(post("/api/raporlar/gonder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    // --- ADMIN ----------------------------------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_kullaniciYonetiminiGorebilir() throws Exception {
        mockMvc.perform(get("/api/kullanicilar")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_auditLoglariGorebilir() throws Exception {
        mockMvc.perform(get("/api/audit-loglar")).andExpect(status().isOk());
    }

    /** app.report.admin-email test ortaminda bos oldugu icin raporGonder gercek SMTP'ye hic gitmez (bkz. RaporService). */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_raporTetikleyebilir() throws Exception {
        mockMvc.perform(post("/api/raporlar/gonder")).andExpect(status().isAccepted());
    }

    // --- Kimliksiz kalmasi GEREKEN uclar -------------------------------------

    /**
     * Prometheus bu ucu 15 saniyede bir kimlik dogrulamasiz kaziyor (bkz. prometheus.yml).
     * Guvenlik kurallari degistirilirken kazara kapatilirsa Grafana panolari sessizce boslar —
     * bu test o sessiz bozulmayi gurultulu hale getirir.
     */
    @Test
    void actuatorPrometheus_kimliksiz_erisilebilir_kalmali() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void actuatorHealth_kimliksiz_erisilebilir_kalmali() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
