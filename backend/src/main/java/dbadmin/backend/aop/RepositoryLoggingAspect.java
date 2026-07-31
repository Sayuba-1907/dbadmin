package dbadmin.backend.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Konsoldaki Hibernate SQL satirlarini hangi repository metodunun tetikledigini gosterir
 * (bkz. backend/notlar, "hibernate ... sonra repository katmanina baglayacagiz").
 * <p>
 * Her repository cagrisini sarar ({@code @Around}), cagri suresince MDC'ye ("repo") metod
 * imzasini yazar. Logback pattern'i ({@code application-dev.properties}) bu MDC alanini
 * {@code %X{repo}} ile her log satirina (SQL dahil) otomatik basar - metodun kendisine ya
 * da SQL loglamasina hic dokunmadan, satirlarin arasina bir "kim cagirdi" etiketi eklenmis
 * olur. Ic ice repository cagrisi ihtimaline karsi (bir repository metodu baska bir
 * repository'yi tetiklerse) eski MDC degeri sonda geri yuklenir, kaybolmaz.
 */
@Aspect
@Component
public class RepositoryLoggingAspect {

    private static final String MDC_KEY = "repo";

    @Around("execution(* dbadmin.backend.repository..*(..))")
    public Object logRepositoryCagrisi(ProceedingJoinPoint joinPoint) throws Throwable {
        String oncekiDeger = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, joinPoint.getSignature().toShortString());
        try {
            return joinPoint.proceed();
        } finally {
            if (oncekiDeger != null) {
                MDC.put(MDC_KEY, oncekiDeger);
            } else {
                MDC.remove(MDC_KEY);
            }
        }
    }
}
