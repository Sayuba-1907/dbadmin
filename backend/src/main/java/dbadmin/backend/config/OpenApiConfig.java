package dbadmin.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI'nin ust kismindaki genel bilgi (baslik/aciklama/versiyon) — springdoc bu Bean'i
 * bulup {@code /v3/api-docs} ve {@code /swagger-ui} sayfalarinda kullanir. Bu olmadan Swagger UI
 * varsayilan jenerik "OpenAPI definition" baslıgini gosterirdi.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dbAdminOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DBAdmin API")
                        .description("Tablo/kolon/schema/tag metadata'sini yonetip, ayni anda gercek "
                                + "Postgres semasini (CREATE/ALTER/DROP TABLE) senkron tutan admin API'si. "
                                + "Her yazma islemi hem metadata'yi hem gercek DB semasini ayni transaction "
                                + "icinde degistirir; biri patlarsa digeri de geri alinir.")
                        .version("v1"));
    }
}
