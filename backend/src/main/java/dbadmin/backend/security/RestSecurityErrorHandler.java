package dbadmin.backend.security;

import dbadmin.backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Guvenlik katmaninin 401/403 cevaplarini uygulamanin geri kalaniyla ayni sekle
 * ({@link ErrorResponse}) sokar.
 *
 * <h2>Bu sinif neden var?</h2>
 * Spring Security istegi <b>servlet filtre zincirinde</b>, DispatcherServlet'e ulasmadan
 * reddeder. {@code @RestControllerAdvice} ise DispatcherServlet'in icinde calisir — yani
 * {@link dbadmin.backend.exception.GlobalExceptionHandler} bu iki durumu <b>hic gormez</b>.
 * Bu sinif olmasaydi 401 ve 403, Spring'in {@code code}/{@code message} icermeyen varsayilan
 * govdesiyle donerdi ve frontend'in {@code err.code}'a bakan cevirisi tam da giris/yetki
 * hatalarinda bos kalirdi.
 *
 * <h2>Dil neden elle cozuluyor?</h2>
 * {@code LocaleContextHolder}'i DispatcherServlet doldurur, ama biz ondan once calisiyoruz —
 * bu yuzden {@code Accept-Language} basligini {@link LocaleResolver}'a dogrudan sorup
 * cozuyoruz (ayni bean, bkz. {@link dbadmin.backend.config.I18nConfig}).
 *
 * <p>Iki arayuzu tek sinifta topluyoruz cunku ikisi de ayni govdeyi uretiyor; metod adlari
 * farkli oldugu icin ({@code commence} / {@code handle}) cakisma olmuyor.
 */
@Component
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public RestSecurityErrorHandler(
            ObjectMapper objectMapper, MessageSource messageSource, LocaleResolver localeResolver) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    /**
     * Kimlik yok ya da gecersiz → 401. Buraya, korunan bir uca token'siz/bozuk token'la
     * gelindiginde dusulur.
     */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        yaz(request, response, HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED",
                "authentication is required, send a valid Bearer token");
    }

    /**
     * Kimlik var ama yetki yetmiyor → 403. Ornek: VIEWER rolundeki bir kullanicinin
     * {@code POST /api/tablolar} denemesi.
     */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        yaz(request, response, HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN",
                "your role is not allowed to perform this operation");
    }

    /** Govdeyi {@link ErrorResponse} seklinde, istegin dilinde yazar. */
    private void yaz(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String fallbackMessage)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                cevir(request, code, fallbackMessage),
                code,
                Map.of());

        // IOException yutulmuyor, cagirana (servlet konteynerine) birakiliyor: bu noktada
        // istemciyle baglanti zaten kopmus demektir, sessizce yutmak sadece sorunu gizlerdi.
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Hata kodunu istegin dilindeki metne cevirir; ceviri yoksa Ingilizce metne duser. */
    private String cevir(HttpServletRequest request, String code, String fallbackMessage) {
        Locale locale = localeResolver.resolveLocale(request);
        try {
            // args = null: {0} yerine {{...}} sozdizimi kullaniyoruz (bkz. GlobalExceptionHandler).
            return messageSource.getMessage(code, null, locale);
        } catch (NoSuchMessageException ex) {
            return fallbackMessage;
        }
    }
}
