package dbadmin.backend;

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * MockMvc'ye Spring Security'nin test koprusunu ({@code springSecurity()}) kurar.
 * Bu olmadan guvenlik filtreleri MockMvc'ye eklenir (kimliksiz istek 401 doner, gercek
 * Bearer token calisir) ama {@code @WithMockUser}'in koydugu kimlik filtre zincirine
 * <b>ulasmaz</b> — cunku zincirdeki SecurityContextHolderFilter baglami kendi
 * deposundan yukler ve testin koydugunu ezer.
 */
@TestConfiguration
public class MockMvcSecurityTestConfig {

    @Bean
    public MockMvcBuilderCustomizer springSecurityCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}
