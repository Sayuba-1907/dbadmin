package dbadmin.backend.security;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.LoginRequest;
import dbadmin.backend.entity.User;
import dbadmin.backend.entity.Role;
import dbadmin.backend.repository.UserRepository;
import dbadmin.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Giristen token almaya, token'la korunan bir uca girmeye kadar tum akis — gercek bir
 * Postgres ve gercek bir filtre zinciri uzerinden (mock kimlik yok, bilerek).
 */
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String KULLANICI = "auth_test_kullanici";
    private static final String PAROLA = "cokGizliParola1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    /** Postgres testler arasinda paylasildigi icin user zaten varsa yeniden kurulmaz. */
    @BeforeEach
    void ensureKullanici() {
        if (!userRepository.existsByUsername(KULLANICI)) {
            userService.createUser(KULLANICI, PAROLA, Role.EDITOR);
        }
    }

    @Test
    void dogruBilgilerle_giris_tokenDoner() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KULLANICI, PAROLA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username", is(KULLANICI)))
                .andExpect(jsonPath("$.role", is("EDITOR")));
    }

    @Test
    void yanlisParola_401Doner() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KULLANICI, "yanlisParola"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_INVALID_CREDENTIALS")));
    }

    /**
     * Olmayan user ile yanlis parola <b>ayni</b> cevabi vermeli — aksi halde hangi
     * user adlarinin kayitli oldugu tek tek denenerek ogrenilebilirdi.
     */
    @Test
    void olmayanKullanici_yanlisParolaylaAyniCevabiVerir() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("boyle_bir_kullanici_yok", "herhangiBirParola"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_INVALID_CREDENTIALS")));
    }

    @Test
    void alinanToken_korunanUctaCalisir() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/tables").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void alinanToken_kimOldugunuDoner() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(KULLANICI)))
                .andExpect(jsonPath("$.role", is("EDITOR")));
    }

    /** EDITOR token'i ADMIN'e ozel uca girememeli — rol token'in icinden okunuyor. */
    @Test
    void editorTokeni_kullaniciYonetimineGiremez() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    void parola_veritabaninaDuzMetinYazilmaz() {
        User user = userRepository.findByUsername(KULLANICI).orElseThrow();

        assertNotEquals(PAROLA, user.getPasswordHash());
        // BCrypt ciktisi her zaman bu onekle baslar; algoritmanin gercekten uygulandiginin isareti.
        assertTrue(user.getPasswordHash().startsWith("$2"),
                "parola BCrypt ile hash'lenmis olmali, bulunan: " + user.getPasswordHash());
    }

    /** requirement notu 9 ("Aktif Oturumlar") — login sonrasi bu oturum listede current=true olarak gorunmeli. */
    @Test
    void girisSonrasi_oturumListesindeSuAnkiOturumGorunur() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.current == true)]").exists());
    }

    /** Bir oturum sonlandirilinca o token'in kendisi de gecersiz olmali (jti artik Redis'te yok). */
    @Test
    void oturumSonlandirilinca_okTokenArtikGecersizOlur() throws Exception {
        String token = tokenAl();
        // Postgres/Redis test siniflari arasinda paylasildigi icin (bkz. AbstractIntegrationTest)
        // bu kullanicinin listesinde ONCEKI testlerden kalma baska oturumlar da olabilir —
        // rastgele ilk elemani degil, ACIKCA current=true olani almak gerekiyor.
        tools.jackson.databind.JsonNode sessions = objectMapper.readTree(
                mockMvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + token))
                        .andReturn().getResponse().getContentAsString());
        String jti = null;
        for (tools.jackson.databind.JsonNode session : sessions) {
            if (session.get("current").asBoolean()) {
                jti = session.get("jti").asString();
            }
        }
        assertTrue(jti != null, "current=true olan bir oturum bulunamadi");

        mockMvc.perform(delete("/api/auth/sessions/" + jti).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /** "Diger cihazlardan cikis yap" — su anki token calismaya devam etmeli, digeri gecersiz olmali. */
    @Test
    void digerCihazlardanCikis_suAnkiHaricHepsiGecersizOlur() throws Exception {
        String tokenEski = tokenAl();
        String tokenYeni = tokenAl();

        mockMvc.perform(post("/api/auth/sessions/revoke-others").header("Authorization", "Bearer " + tokenYeni))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenEski))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokenYeni))
                .andExpect(status().isOk());
    }

    private String tokenAl() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KULLANICI, PAROLA))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }
}
