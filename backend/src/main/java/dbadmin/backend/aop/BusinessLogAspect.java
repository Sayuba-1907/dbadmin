package dbadmin.backend.aop;

import java.util.Arrays;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link BusinessLog} isaretli bir metod basariyla donunce INFO seviyede bir satir basar.
 * Logback pattern'i (application-dev.properties) MDC'deki traceId/spanId'i otomatik her satira
 * ekledigi icin bu loglar da ayri bir altyapi gerektirmeden ilgili trace'e baglanir (Req-2.3).
 * <p>
 * Bilinen sinirlar:
 * <li>Self-invocation'i (ayni sinif icinden {@code this.} ile cagri) tetiklemez — Spring AOP
 * proxy-tabanli oldugu icin ({@link RepositoryLoggingAspect} ile ayni sinir).
 * <li>{@code @AfterReturning} sadece BASARILI donuste calisir; hata/rollback durumunda log
 * basilmaz — DDL rollback olduğunda audit log da yazilmadigi gibi bu da tutarli kabul edilir.
 */
@Aspect
@Component
public class BusinessLogAspect {

    private static final Logger log = LoggerFactory.getLogger("dbadmin.business");

    @AfterReturning("@annotation(businessLog)")
    public void logBusinessEvent(JoinPoint joinPoint, BusinessLog businessLog) {
        log.info("{} - {}({})", businessLog.value(), joinPoint.getSignature().getName(),
                Arrays.toString(joinPoint.getArgs()));
    }
}
