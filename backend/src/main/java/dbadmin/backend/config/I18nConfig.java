package dbadmin.backend.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Backend tarafi i18n (internationalization) ayari: hata mesajlarinin istemcinin diline gore
 * donmesini saglar.
 * <p>
 * Dil secimi {@code Accept-Language} HTTP basligindan okunur — tarayicilar ve HTTP istemcileri
 * bu basligi zaten gonderir, yani istemcinin ekstra bir sey yapmasi gerekmez. Ozel bir
 * {@code ?lang=tr} parametresi ya da cookie tercih etmedik: bu API'yi cagiran taraf (frontend,
 * Postman, baska bir servis) HTTP'nin kendi standart mekanizmasini kullansin.
 * <p>
 * Desteklenen diller {@code tr} ve {@code en}; baslik hic gelmezse ya da desteklenmeyen bir dil
 * istenirse {@link #DEFAULT_LOCALE} (Ingilizce) kullanilir — mesaj metinleri
 * {@code messages.properties} (Ingilizce, varsayilan) ve {@code messages_tr.properties}
 * dosyalarinda, hata kodlarina gore anahtarlanmis halde durur.
 * <p>
 * Not: frontend hata mesajlarini hala kendisi cevirir ({@code code} alanina bakip i18next
 * sozlugunden). Bu ayar frontend icin degil, API'yi dogrudan kullanan taraflar (Swagger,
 * Postman, curl, loglar, ileride baska istemciler) icindir — kullanici arayuzunde dil
 * degistirildiginde ekranda duran mesajin da aninda degismesi gerektigi icin oradaki ceviri
 * sunucuya bagimli olmamali.
 */
@Configuration
public class I18nConfig {

    /** Baslik yoksa ya da desteklenmeyen bir dil istenirse kullanilacak dil. */
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    /** Uygulamanin cevirisi olan diller; bu listede olmayan bir dil istenirse varsayilana duser. */
    public static final List<Locale> SUPPORTED_LOCALES =
            List.of(Locale.ENGLISH, Locale.forLanguageTag("tr"));

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }
}
