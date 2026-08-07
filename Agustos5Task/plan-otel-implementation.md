# PLAN: OpenTelemetry (Log + Metric + Trace) İmplementasyonu

`requirement-otel-observability.md`'nin uygulama planı. Redis planındaki fazlama mantığı izlenmiştir
(bkz. `requirement-redis-user-cache.md` §4).

## Genel Mimari

```
Spring Boot uygulaması (micrometer-tracing + OTel bridge)
   │  traces + logs (trace_id ile isaretli) + mevcut Prometheus metrikleri
   ▼
OTel Collector (dbadmin-net icinde, disari kapali)
   │
   ├─► Traces → Tempo
   ├─► Logs   → Loki
   └─► Metrics → (dokunulmuyor, Prometheus zaten dogrudan scrape ediyor)
                     │
                     ▼
                  Grafana (zaten var) — Explore ekraninda trace_id ile log/metric arasi gecis
```

Not: Collector olmadan da Tempo/Loki'ye doğrudan export edilebilir (daha az container). Ama tek bir
toplama/yönlendirme noktası olması, ileride başka bir backend'e (örn. Jaeger) geçişi tek dosya
değişikliğine indirdiği için Collector tercih ediliyor — Redis'te "mekanizma görünür olsun" tercihiyle
aynı gerekçe: yönlendirme kararı konfigürasyonda açık, kodda gizli değil.

## Faz 1: Altyapı — Docker Compose'a Yeni Servisler

- Adım 1.1: `docker-compose.yml`'e üç servis ekle: `otel-collector`, `tempo`, `loki`. Hepsi
  `dbadmin-net` içinde, `redis` emsalinde sadece `expose` (dışarı `ports` açılmayacak) — tek istisna
  geliştirme sırasında Tempo/Loki arayüzlerine tarayıcıdan bakmak gerekirse geçici port açımı.
- Adım 1.2: `otel-collector-config.yaml` dosyası oluştur (repo köküne, `prometheus.yml` emsalinde) —
  receiver: OTLP (gRPC/HTTP), exporter: Tempo (trace) + Loki (log).
- Adım 1.3: `grafana/provisioning/datasources/datasource.yml`'e Tempo ve Loki datasource'larını ekle
  (Prometheus datasource'u zaten orada, aynı dosyaya iki blok daha).
- Adım 1.4: Tempo/Loki için minimal retention ayarla (örn. 48 saat) — dev ortamı, sınırsız
  saklamaya gerek yok.

## Faz 2: Backend Bağımlılıkları ve Temel Trace Üretimi

- Adım 2.1: `pom.xml`'e ekle: `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`.
  Java agent kullanılmayacak (Req-3.2), bu yüzden `-javaagent` flag'i **eklenmeyecek**.
- Adım 2.2: `application.properties`'e OTLP endpoint'i (`management.otlp.tracing.endpoint`) ve
  `management.tracing.sampling.probability=1.0` (dev'de %100 örnekleme, Req-2.5) ekle.
- Adım 2.3: `docker-compose.yml`'deki `backend` servisine `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317`
  environment değişkenini ekle.
- Adım 2.4: Uygulamayı ayağa kaldırıp tek bir GET isteği at, Tempo arayüzünde (veya Grafana Explore
  → Tempo) bir trace'in gerçekten düştüğünü doğrula — bu noktada sadece otomatik HTTP+JDBC span'leri
  var, henüz özel span yok.

## Faz 3: Dual-Write Ayrımını Trace'e Taşı (Req-2.2)

- Adım 3.1: `TabloService`, `SchemaService` içindeki metadata-yazma ve DDL-çalıştırma adımlarını,
  `io.micrometer.observation.ObservationRegistry` veya doğrudan `Tracer.nextSpan()` ile elle
  isimlendirilmiş span'lere sar: `metadata-write` ve `ddl-execute`.
- Adım 3.2: DDL span'ine (`ddl-execute`) çalıştırılan tabloyu/kolonu tanımlayan attribute'lar ekle
  (örn. `db.table.name`) — ama **asla** ham SQL parametre değeri veya kullanıcı girdisi ham haliyle
  eklenmeyecek (Req-3.5, injection yüzeyiyle aynı hassasiyet: `NameValidator`'dan geçmiş isim
  kullanılacak).
- Adım 3.3: `TableDdlExecutor`/`SchemaDdlExecutor` seviyesinde de aynı isimlendirme kuralını uygula,
  böylece hangi DDL'in hangi servis çağrısından geldiği span attribute'larından ayırt edilebilsin.

## Faz 4: Log-Trace Köprüsü (Req-2.3)

- Adım 4.1: `logback-spring.xml` (veya `application.properties` pattern'i) içine MDC üzerinden
  `trace_id`/`span_id`'i log satırına ekleyen pattern ekle — Micrometer Tracing bunu Spring Boot
  ile otomatik MDC'ye yazar, sadece log pattern'inin bunu basması gerekir.
- Adım 4.2: Logback'e OTLP log appender (veya Loki'ye doğrudan giden bir appender) ekle, böylece
  loglar hem konsola hem Collector'a gitsin.
- Adım 4.3: `application-dev.properties`'teki mevcut Hibernate/JdbcTemplate logger'ları dokunulmadan
  kalsın — bu faz sadece **nereye gittiklerini** değiştiriyor (ek olarak Loki'ye de gidiyorlar),
  formatlarını veya seviyelerini değiştirmiyor.
- Adım 4.4 (Business loglar, Req-2.6 — annotation + aspect ile, AOP öğrenme amaçlı): Elle
  `log.info(...)` eklemek yerine, deklaratif bir `@BusinessLog` custom annotation'ı ve onu işleyen
  bir aspect yazılacak:
  - Adım 4.4.1: `aop/BusinessLog.java` — `@Retention(RUNTIME) @Target(METHOD)`, tek bir
    `value()` alanı (işlem adı, örn. `"tablo-olusturuldu"`).
  - Adım 4.4.2: `aop/BusinessLogAspect.java` — `@AfterReturning("@annotation(businessLog)")` ile
    metod başarıyla döndüğünde INFO seviye log basar (metod adı + parametreler + işlem adı).
    Mevcut `RepositoryLoggingAspect`'in MDC deseniyle tutarlı kalır; bu satırlar da Adım 4.1'deki
    MDC köprüsü sayesinde otomatik `trace_id` taşır.
  - Adım 4.4.3: `TabloService.createTablo/deleteTablo`, `SchemaService.createSchema/updateSchema/
    deleteSchema`, `TagService.*` metodlarına `@BusinessLog("...")` ekle.
  - Not (self-invocation): Spring AOP proxy tabanlı olduğu için aynı sınıf içinden `this.` ile
    yapılan çağrılarda annotation tetiklenmez — servis metodları controller'dan çağrıldığı sürece
    sorun yok, ama bir servis metodu başka bir servis metodunu `this.` ile çağırırsa log sessizce
    kaybolur. Bilinen bir sınır, ekstra kod ile çözülmeyecek (proxy'yi bypass etmenin yolları
    — self-injection vb. — gereksiz karmaşıklık sayılır).
  - Not (hata durumunda log basılmaz): `@AfterReturning` sadece başarılı dönüşte çalışır — DDL
    rollback olduğunda audit log da yazılmadığı gibi (bkz. `requirement-audit-log.md` Req-3.1),
    business log da yazılmaz; bu tutarlı kabul edilir, `@Around`'a geçmek gerekmez.
  - Audit log ile karıştırılmamalı: bu loglar Loki'nin kısa retention'ında yaşar, kalıcı kayıt
    değildir (bkz. `plan-audit-log-implementation.md`).

## Faz 5: Redis Span'lerinde Hata Görünürlüğü (Req-2.4)

- Adım 5.1: `KullaniciRolCacheService`'in try-catch bloklarında, hatayı yutmadan **önce** aktif
  span'i `span.recordException(e)` / `span.setStatus(ERROR)` ile işaretle — davranış (fail-open,
  hatayı yukarı fırlatmama) hiç değişmiyor, sadece trace'e "burada bir sorun oldu ama akış devam
  etti" bilgisi ekleniyor.

## Faz 6: Doğrulama

- Adım 6.1: Bir `createTablo` isteği at, Tempo'da tek trace altında sırayla: HTTP span → service
  span → `metadata-write` (Hibernate INSERT'leri altında) → `ddl-execute` (CREATE TABLE) span'lerini
  görsel olarak doğrula.
- Adım 6.2: Aynı trace'in `trace_id`'siyle Grafana Explore → Loki'de o isteğe ait log satırlarını
  filtrele, eşleştiğini doğrula.
- Adım 6.3: Redis container'ı durdurup bir istek at, ilgili span'in error olarak işaretlendiğini ama
  isteğin yine de 2xx döndüğünü doğrula (fail-open regresyonu olmadığını kanıtlar).
- Adım 6.4: `dbadmin-backend.json` Grafana panosunun hâlâ eskisi gibi çalıştığını (Prometheus tarafı
  bozulmadı) doğrula.

## Sırası Önemli Notlar

- Faz 1-2 olmadan Faz 3-5'in test edilecek bir yeri yok; sıra bu yüzden altyapı → temel trace →
  özelleştirme şeklinde.
- Faz 3, projenin en özgün kısmı — otomatik enstrümantasyonun *gösteremediği* şeyi (dual-write
  ayrımı) gösterdiği için CLAUDE.md'deki temel gözlemlenebilirlik açığını gerçekten kapatan adım
  burası. Zaman kısıtlıysa Faz 4-5 ertelenebilir, Faz 3 ertelenmemeli.

## Tahmini Kapsam

Redis entegrasyonundan (tek servis + tek cache katmanı) daha büyük: 3 yeni container, iki yeni
bağımlılık, log pattern değişikliği, iki serviste elle span ekleme. Yine de her fazın kendi başına
test edilebilir/doğrulanabilir çıktısı olduğu için parça parça (haftalık PR akışına uygun şekilde)
ilerlenebilir.
