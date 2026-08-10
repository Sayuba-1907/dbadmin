package dbadmin.backend.exception;

import dbadmin.backend.dto.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Merkezi hata yakalama noktasi. {@code @RestControllerAdvice}, hem uygulamanin kendi
 * exception'larini (Validation/NotFound/Conflict) hem de Spring'in girdi/istek katmaninda
 * kendiliginden firlattigi exception'lari (gecersiz path param, bozuk JSON, olmayan yol,
 * desteklenmeyen HTTP metodu, DB constraint ihlali) tek yerden yakalar ve hepsini ayni govde
 * sekline ({@link ErrorResponse}) cevirir — frontend hangi tur hata oldugunu bilmeden hep ayni
 * alanlardan okuyabilir. Bu ikinci grup olmadan (yakalanmadan) Spring'in kendi varsayilan hata
 * govdesine ({@code {"status":..,"error":..,"path":..}}, {@code code}/{@code message} olmadan)
 * dusuluyordu — frontend'in {@code err.code}'a bakan cevirisi bu durumlarda bos kaliyordu.
 * <p>
 * Mesajin dili de burada belirlenir: exception'in kendi Ingilizce metni yerine, {@code code}
 * anahtariyla {@code messages*.properties} dosyalarindan istegin diline uygun metin aranir
 * (bkz. {@link dbadmin.backend.config.I18nConfig}). Dil {@code Accept-Language} basligindan
 * gelir; Spring bunu {@link LocaleContextHolder}'a koyar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), ex.getCode(), ex.getDetails());
    }

    /** MinIO'ya yedek yuklenemedi (Req-3.3, fail-closed) — hata disaridaki bir bagimliliktan geldigi icin 502. */
    @ExceptionHandler(BackupFailedException.class)
    public ResponseEntity<ErrorResponse> handleBackupFailed(BackupFailedException ex) {
        log.error("audit log yedegi MinIO'ya yuklenemedi", ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex.getCode(), Map.of());
    }

    /** {@code GET /api/tablolar/abc} — {@code id} path parametresi Long'a cevrilemiyor. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String type = ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("parameter", ex.getName());
        details.put("type", type);
        details.put("value", String.valueOf(ex.getValue()));
        return build(HttpStatus.BAD_REQUEST,
                ex.getName() + " must be a valid " + type + ": " + ex.getValue(),
                "VALIDATION_INVALID_PATH_PARAM", details);
    }

    /** Istek govdesi JSON olarak parse edilemedi (bozuk/eksik/yanlis tip) ya da hic gonderilmedi. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException ex) {
        // ex.getMessage() Jackson'in ham parser hatasini icerir (satir/sutun, internal sinif
        // adlari) — istemciye sadece kod+jenerik metin, ayrintiyi sunucu logunda tutuyoruz.
        log.debug("malformed request body", ex);
        return build(HttpStatus.BAD_REQUEST, "request body is missing or not valid JSON",
                "VALIDATION_MALFORMED_BODY", Map.of());
    }

    /** Var olmayan bir HTTP yoluna istek atildi (ör. yanlis yazilmis endpoint). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "no endpoint at this path", "NOT_FOUND_ENDPOINT",
                Map.of("path", ex.getResourcePath()));
    }

    /** Yol var ama bu HTTP metoduyla desteklenmiyor (ör. GET-only bir uca PUT atmak). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        String supported = ex.getSupportedMethods() == null ? "" : String.join(", ", ex.getSupportedMethods());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("method", ex.getMethod());
        details.put("supported", supported);
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "method " + ex.getMethod() + " is not supported here, use one of: " + supported,
                "VALIDATION_METHOD_NOT_ALLOWED", details);
    }

    /**
     * Postgres bir constraint'i reddetti ve bu, uygulamanin kendi exception'larindan biri
     * degil — bilinen ornek: {@code PATCH .../primary-key} sirasinda tabloda o kolonu NULL
     * olan ya da tekrar eden degerler iceren satirlar varsa {@code ADD CONSTRAINT ... PRIMARY
     * KEY} Postgres tarafindan reddedilir (bkz. TableService.changeColumnPrimaryKey javadoc'u).
     * Ham DB hatasi (tablo/kolon adlari, constraint ismi) istemciye degil sunucu loguna gider.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("data integrity violation", ex);
        return build(HttpStatus.CONFLICT,
                "the operation conflicts with the table's current data "
                        + "(e.g. a column being made primary key contains null or duplicate values)",
                "CONFLICT_COLUMN_NOT_UNIQUE", Map.of());
    }

    /**
     * Giris denemesi basarisiz: kullanici adi yok ya da parola yanlis. Bu exception
     * {@code AuthController.login} icinde, yani DispatcherServlet'in <b>icinde</b> firlatildigi
     * icin buradan yakalanabiliyor — filtre zincirinde (token'siz/gecersiz token) olusan 401'ler
     * ise buraya hic ulasmaz, onlari {@code RestSecurityErrorHandler} karsilar.
     * <p>
     * Mesaj bilerek belirsiz: "boyle bir kullanici yok" ile "parola yanlis" ayirt edilemezse,
     * hangi kullanici adlarinin kayitli oldugu tek tek denenerek ogrenilemez.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.debug("basarisiz giris denemesi", ex);
        return build(HttpStatus.UNAUTHORIZED, "invalid username or password",
                "AUTH_INVALID_CREDENTIALS", Map.of());
    }

    /**
     * Yetki yetmiyor ve bu, controller/servis katmaninda anlasilmis. Yol bazli kurallarin
     * reddettigi istekler {@code RestSecurityErrorHandler}'a duser; bu handler ileride metod
     * bazli guvenlik ({@code @PreAuthorize}) eklenirse devreye girecek olan yedegidir.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "your role is not allowed to perform this operation",
                "AUTH_FORBIDDEN", Map.of());
    }

    /**
     * Son care: yukarida yakalanmayan her sey. Ham exception mesaji istemciye asla gitmez —
     * stack trace'ler dahili detay (SQL, dosya yolu, sinif adi) sizdirabilir; sadece sunucu
     * loguna yazilir, istemci jenerik bir mesaj + kod alir.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "an unexpected error occurred", "INTERNAL_ERROR", Map.of());
    }

    /** Ayni sekildeki hata govdesini uretmek icin ortak yardimci — 3 handler da bunu cagirir. */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String fallbackMessage, String code, Map<String, String> details) {
        String message = localize(code, details, fallbackMessage);
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, code, details));
    }

    /**
     * Hata kodunu istegin dilindeki metne cevirir. Ceviri bulunamazsa exception'in kendi
     * Ingilizce mesajina duser — yani yeni bir hata kodu eklenip {@code messages.properties}'e
     * yazilmasi unutulursa istek patlamaz, sadece mesaj cevrilmemis kalir.
     */
    private String localize(String code, Map<String, String> details, String fallbackMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            // args = null: Spring'in MessageFormat isletmesini bilerek devre disi birakiyoruz.
            // Sablonlarimiz {0} yerine {{name}} kullaniyor (frontend'in ceviri dosyalariyla ayni
            // sozdizimi), yer tutuculari asagida kendimiz dolduruyoruz. Boylece hangi degerin
            // nereye gittigi siraya degil isme bagli oluyor.
            String template = messageSource.getMessage(code, null, locale);
            return fillPlaceholders(template, details);
        } catch (NoSuchMessageException ex) {
            return fallbackMessage;
        }
    }

    /** {@code {{name}}} / {@code {{id}}} gibi yer tutuculari details map'indeki degerlerle degistirir. */
    private String fillPlaceholders(String template, Map<String, String> details) {
        String result = template;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
