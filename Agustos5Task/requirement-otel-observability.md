# GÖREV: OpenTelemetry ile Log + Metric + Trace Birleştirmesi

`backend/notlar`, "Daha ne yapabiliriz" maddesine bağlı, gözlemlenebilirlik altyapısını (Prometheus +
Grafana zaten var) trace ve merkezi log toplama ile tamamlamak için requirement.

## 0. OTel Nedir, Ne İşe Yarar (Arka Plan)

OpenTelemetry (OTel), log + metric + trace'i **tek bir standart ve tek bir `trace_id` altında**
birbirine bağlayan, vendor-neutral bir gözlemlenebilirlik (observability) framework'üdür. CNCF
projesi olduğu için hiçbir ürüne kilitlenmez — aynı veri Grafana Tempo'ya da, Jaeger'a da,
Datadog'a da gönderilebilir.

**Üç sinyal (pillar):**

- **Metric** — sayısal, zaman içinde toplanan ölçüm (örn. "şu an kaç istek/sn"). Projede zaten var:
  Micrometer → Prometheus → Grafana.
- **Log** — bir olayın metni. Projede zaten var ama sadece konsola/dosyaya basılıyor, merkezi bir
  toplama/arama yeri yok.
- **Trace** — bir isteğin sistem içinde izlediği yolun ağaç yapısı (span'ler). **Projede hiç yok.**

**Bizde bugün ne eksik, OTel bunu nasıl kapatıyor:**

CLAUDE.md'nin işaret ettiği temel gözlemlenebilirlik açığı şu: her yazma işlemi metadata (Hibernate)
+ gerçek DDL (`JdbcTemplate`) olmak üzere **iki ayrı yoldan** geçiyor, ama bunlar iki ayrı logger'a
basıyor (`org.hibernate.SQL` ve `org.springframework.jdbc.core.JdbcTemplate`) ve birbirine
bağlanmıyor. Bir `createTablo()` isteğinde hangi SQL'in metadata'dan hangisinin DDL'den geldiğini
görmek için iki logu elle eşleştirmek gerekiyor. Trace ile bu iki adım, tek bir isteğin altında iki
ayrı child span olarak otomatik görünür — sırası, süresi ve hangi transaction'a ait oldukları
birlikte.

## 1. Temel Amacımız (Epic / User Story)

Bir HTTP isteğinin backend içinde izlediği tüm yolu (controller → service → Hibernate/JdbcTemplate →
Redis → Postgres) tek bir `trace_id` ile uçtan uca izleyebilmek; bu trace'i aynı isteğin loglarıyla
ve mevcut Prometheus metrikleriyle Grafana üzerinden ilişkilendirebilmek.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Otomatik span'ler):** Her gelen HTTP isteği bir root span olarak başlayacak; JDBC
  (Postgres) çağrıları ve Redis çağrıları otomatik olarak child span üretecek.
- **Req-2.2 (Dual-write ayrımı görünür olsun):** `TabloService`/`SchemaService` içindeki metadata
  yazma adımı ile DDL çalıştırma adımı ayrı, isimlendirilmiş span'ler olarak görünecek (örn.
  `metadata-write` / `ddl-execute`), otomatik JDBC span'lerine güvenilmeyecek çünkü ikisi de aynı
  `JdbcTemplate`/Hibernate katmanından geçtiği için otomatik enstrümantasyon bunları
  ayırt etmeyebilir.
- **Req-2.3 (Log-trace ilişkilendirme):** Uygulama loglarının her satırına aktif `trace_id` ve
  `span_id` eklenecek (MDC üzerinden), böylece bir trace'e Grafana'da tıklandığında o isteğe ait
  loglar filtrelenebilecek.
- **Req-2.4 (Redis fail-open görünürlüğü):** `KullaniciRolCacheService`'in try-catch ile yuttuğu
  Redis hataları artık sessiz kalmayacak; ilgili span "hata" (error) olarak işaretlenecek, böylece
  cache'in ne sıklıkla fail-open'a düştüğü trace üzerinden görülebilecek.
- **Req-2.5 (Örnekleme):** Geliştirme/staj ortamında her istek trace'lenecek (sampling=%100); bu,
  gerçek prod trafiği olmadığı ve amacın öğrenme olduğu için kabul edilebilir bir maliyettir.
- **Req-2.6 (Business loglar):** Teknik loglara (Hibernate SQL, JdbcTemplate DDL) ek olarak, servis
  katmanına iş anlamı taşıyan INFO seviye loglar eklenecek (örn. "tablo oluşturuldu: id=5,
  schema=public"). Bunlar Req-2.3'teki MDC köprüsü sayesinde otomatik olarak `trace_id` taşıyacak,
  ayrı bir altyapı gerektirmeyecek — sadece ilgili `TabloService`/`SchemaService`/`TagService`
  metodlarına log satırı eklemek yeterli. Not: bu, kalıcı/hesap-verebilirlik amaçlı **audit log**
  ile karıştırılmamalı — business log Loki'nin kısa retention'ında (Req-3.6) yaşar ve amacı
  debug/gözlemleme, audit log ise `requirement-audit-log.md`'de ayrı ele alınır ve DB'de kalıcı
  yaşar.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Mevcut Prometheus/Grafana korunacak):** Micrometer → Prometheus akışına dokunulmayacak;
  OTel bunun *yerine değil*, *yanına* eklenecek. `dbadmin-backend.json` panosu bozulmayacak.
- **Req-3.2 (Mekanizma görünür kalsın):** Projenin genel tercihi (bkz. `DECISIONS.md`: JWT elle
  yazıldı, Redis cache elle yazıldı — "mekanizma görünür olsun" gerekçesiyle) burada da geçerli:
  tam kara kutu olan Java agent (`-javaagent:opentelemetry-javaagent.jar`) yerine, Spring Boot'un
  native `micrometer-tracing` köprüsü ve gerekli yerlerde elle açılan span'ler tercih edilecek.
- **Req-3.3 (Docker izolasyonu):** Yeni eklenecek toplama bileşenleri (Collector, Tempo, Loki) Redis
  gibi sadece `dbadmin-net` içinden erişilebilir olacak; dışarıya gereksiz port açılmayacak
  (bkz. Req-3.7 emsali, `requirement-redis-user-cache.md`).
- **Req-3.4 (Fail-open):** Trace/log toplama backend'i (Collector/Tempo/Loki) çökerse uygulama
  isteklere cevap vermeye devam edecek; OTel exporter'ları arka planda, timeout'lu ve
  best-effort çalışacak (varsayılan OTel SDK davranışı zaten budur, ekstra kod gerekmez).
  Redis fail-open ile aynı felsefe: gözlemlenebilirlik bir bağımlılık değil, bir optimizasyondur.
- **Req-3.5 (Hassas veri sızmasın):** Span attribute'larına parola hash'i, JWT secret'ı veya
  `APP_JWT_SECRET` gibi değerler asla eklenmeyecek; SQL span'lerinde parametre değerleri değil
  sadece sorgu şekli (statement) loglanacak — CLAUDE.md'nin `bind` parametre logu için verdiği
  "verbose ve düz metin şifre riski" uyarısı burada da geçerli.
- **Req-3.6 (Kaynak tüketimi):** Yeni container'lar (Collector + Tempo + Loki, ya da tek başına
  Grafana Alloy) mevcut `docker compose up -d --build` akışına eklenecek, host makinede aşırı
  kaynak tüketmeyecek şekilde (dev/staj ortamı için minimal retention, örn. Tempo/Loki 24-48 saat
  saklama) yapılandırılacak.
- **Req-3.7 (Test edilebilirlik):** En az bir entegrasyon senaryosu (örn. tablo oluşturma isteği)
  ile trace'in gerçekten üretildiği ve `metadata-write`/`ddl-execute` span'lerinin ayrı ayrı
  görünür olduğu manuel olarak (Grafana Tempo arayüzünden) doğrulanacak; otomatik test şart değil
  (trace altyapısını test etmek, iş mantığını test etmekten farklı bir yatırım gerektirir).

## 4. Kapsam Dışı (Bu Aşamada Yapılmayacak)

- Frontend'e (React) OTel Web SDK ile client-side trace eklenmesi — backend tarafı oturunca ayrı bir
  requirement olarak ele alınabilir.
- Prod-grade alerting (OTel Collector üzerinden anomaly detection) — Grafana'nın mevcut
  `alerting/rules.yaml` mekanizması metric tarafında zaten var, trace tarafına genişletmek ayrı iş.
- Sampling stratejisi optimizasyonu (tail-based sampling vb.) — tek kullanıcılı staj ortamında
  gereksiz karmaşıklık.

## 5. Durum

2026-08-05/06'da implemente edildi (backend/week3): Faz 1-6 tamamlandı — Tempo/Loki container'ları,
`micrometer-tracing` + OTel bridge, `SpanRunner` ile `metadata-write`/`ddl-execute` span ayrımı,
MDC üzerinden log-trace köprüsü, `@BusinessLog`/`BusinessLogAspect`, Redis span hata görünürlüğü
(`UserRoleCacheService`), Grafana Explore üzerinden uçtan uca doğrulama. Yol boyunca alınan kararlar
(OTel Collector'ın bilinçli olarak atlanması, `TracingAwareRedisCacheWriter`'in neden gerektiği) için
bkz. `DECISIONS.md` "OpenTelemetry gozlemlenebilirlik" bölümü. `tempo.yaml`'e `loki.yaml` ile
tutarlı 48 saatlik `compactor.compaction.block_retention` eklendi (Req-3.6). Uygulama planı için
`plan-otel-implementation.md` dosyasına bakınız.
