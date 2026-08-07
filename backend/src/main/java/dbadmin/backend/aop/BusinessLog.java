package dbadmin.backend.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Isaretlenen metod basariyla donunce (bkz. BusinessLogAspect) INFO seviyede is-anlami tasiyan
 * bir log satiri basar (ör. "tablo oluşturuldu"). Teknik loglardan (Hibernate SQL, JdbcTemplate
 * DDL) farkli olarak "hangi SQL calisti" degil "hangi is islemi tamamlandi" sorusuna cevap verir.
 * <p>
 * Bu, kalici/hesap-verebilirlik amacli AUDIT LOG degildir — bu satirlar Loki'nin kisa
 * retention'inda yasar, amac debug/gozlemleme. Audit log ayri bir konu (bkz. requirement-audit-log.md).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BusinessLog {
    String value();
}
