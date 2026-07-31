package dbadmin.backend.security;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dbadmin.backend.AbstractIntegrationTest;
import dbadmin.backend.dto.LoginRequest;
import dbadmin.backend.entity.Kullanici;
import dbadmin.backend.entity.Rol;
import dbadmin.backend.repository.KullaniciRepository;
import dbadmin.backend.service.KullaniciService;
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
    private KullaniciService kullaniciService;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    /** Postgres testler arasinda paylasildigi icin kullanici zaten varsa yeniden kurulmaz. */
    @BeforeEach
    void ensureKullanici() {
        if (!kullaniciRepository.existsByKullaniciAdi(KULLANICI)) {
            kullaniciService.createKullanici(KULLANICI, PAROLA, Rol.EDITOR);
        }
    }

    @Test
    void dogruBilgilerle_giris_tokenDoner() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(KULLANICI, PAROLA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.kullaniciAdi", is(KULLANICI)))
                .andExpect(jsonPath("$.rol", is("EDITOR")));
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
     * Olmayan kullanici ile yanlis parola <b>ayni</b> cevabi vermeli — aksi halde hangi
     * kullanici adlarinin kayitli oldugu tek tek denenerek ogrenilebilirdi.
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

        mockMvc.perform(get("/api/tablolar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void alinanToken_kimOldugunuDoner() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/auth/ben").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kullaniciAdi", is(KULLANICI)))
                .andExpect(jsonPath("$.rol", is("EDITOR")));
    }

    /** EDITOR token'i ADMIN'e ozel uca girememeli — rol token'in icinden okunuyor. */
    @Test
    void editorTokeni_kullaniciYonetimineGiremez() throws Exception {
        String token = tokenAl();

        mockMvc.perform(get("/api/kullanicilar").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_FORBIDDEN")));
    }

    @Test
    void parola_veritabaninaDuzMetinYazilmaz() {
        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(KULLANICI).orElseThrow();

        assertNotEquals(PAROLA, kullanici.getParolaHash());
        // BCrypt ciktisi her zaman bu onekle baslar; algoritmanin gercekten uygulandiginin isareti.
        assertTrue(kullanici.getParolaHash().startsWith("$2"),
                "parola BCrypt ile hash'lenmis olmali, bulunan: " + kullanici.getParolaHash());
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
