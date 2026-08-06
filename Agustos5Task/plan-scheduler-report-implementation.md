# PLAN: Saatlik Otomatik Rapor Maili (Scheduler + Async) İmplementasyonu

`requirement-scheduler-report.md`'nin uygulama planı.

## Faz 0: Ön Koşul (Blocking)

- Adım 0.1: `plan-audit-log-implementation.md` Faz 1-3 tamamlanmış olmalı — en azından
  `AuditLogRepository`'nin tarih aralığına göre sorgulanabilir olması gerekiyor (Req-2.2). Bu faz
  bitmeden Faz 3'e geçilmeyecek.

## Faz 1: Bağımlılıklar ve Yapılandırma

- Adım 1.1: `pom.xml`'e `spring-boot-starter-mail` ekle.
- Adım 1.2: `application.properties`'e ekle: `spring.mail.host`, `spring.mail.port`,
  `spring.mail.username`, `spring.mail.password` (hepsi `${...}` ile env'den), ve
  `app.report.admin-email` (alıcı adres, Req-2.3).
- Adım 1.3: `docker-compose.yml`'deki `backend` servisinin `environment` bloğuna ekle:
  `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
  (Gmail app password), `APP_REPORT_ADMIN_EMAIL` — JWT secret ile aynı satırda "bu değer .env'den
  gelmeli, sabit yazılmamalı" yorumu eklenecek.
- Adım 1.4: `.env`'e yeni değişkenleri ekle (gerçek Gmail app password kullanıcı tarafından
  üretilip elle girilecek — bu adım otomatikleştirilemez, kullanıcıdan bilgi istenecek).

## Faz 2: Async Altyapısı

- Adım 2.1: `config/AsyncConfig.java` — `@EnableAsync` + `@Bean("raporTaskExecutor")` ile
  `ThreadPoolTaskExecutor` (core=1, max=2, queueCapacity=10). Bean adı verilecek çünkü birden
  fazla `TaskExecutor` bean'i varsa Spring hangisini kullanacağını bilemez (`@Async("raporTaskExecutor")`
  ile açıkça belirtilecek) — Req-3.1'in gerekçesi burada koda dökülüyor.

## Faz 3: Rapor Oluşturma ve Gönderme Servisi

- Adım 3.1: `service/RaporService.java` oluştur, iki sorumluluk net ayrılacak:
  - `String raporIcerigiOlustur()` — senkron, `SchemaRepository.count()`, `TabloRepository.count()`,
    `KolonRepository.count()`, `KullaniciRepository.count()` ile anlık görüntüyü, ve
    `AuditLogRepository`'den son 1 saatlik kayıtları düz metne çevirir.
  - `@Async("raporTaskExecutor") void raporGonder(String icerik)` — `JavaMailSender` ile
    `app.report.admin-email`'e mail atar; try-catch ile sarılı, hata ERROR loglanır ve yutulur
    (Req-3.2, fail-open — yukarı fırlatılmaz).
- Adım 3.2: İki metodun ayrı olması bilinçli: `raporIcerigiOlustur()` DB'ye bağımlı ve test edilmesi
  kolay olmalı (Req-3.7); `raporGonder()` ise sadece SMTP'ye bağımlı, mail gönderimini mock'layarak
  ayrı test edilebilir.

## Faz 4: Scheduler

- Adım 4.1: `scheduler/RaporScheduler.java` — `@EnableScheduling` (ana config sınıfında veya
  `BackendApplication`'da), `@Scheduled(cron = "0 0 * * * *")` ile işaretli tek bir metod:
  `raporService.raporIcerigiOlustur()` çağırıp sonucu `raporService.raporGonder(icerik)`'e verir.
- Adım 4.2: `@Scheduled` metodunun kendisi `@Async` **olmayacak** — scheduler zaten Spring'in kendi
  ayrı task scheduler thread'inde çalışır; asıl bloklanmaması gereken yer SMTP çağrısı (Adım 3.1),
  o yüzden `@Async` sadece `raporGonder`'da.

## Faz 5: Manuel Tetikleme Ucu

- Adım 5.1: `controller/RaporController.java` — `POST /api/raporlar/gonder`, `RaporScheduler`'daki
  aynı iki adımı (`raporIcerigiOlustur` + `raporGonder`) çağırır, `202 Accepted` döner (mail
  gönderimi async olduğu için istek anında biter, gönderimin başarılı olup olmadığı senkron
  bilinmez).
- Adım 5.2: `SecurityConfig`'e `/api/raporlar/**` için `hasRole(ADMIN)` kuralı ekle —
  `/api/kullanicilar/**` ve (implemente edildiyse) `/api/audit-loglar/**` kurallarının yanına,
  genel `/api/**` kuralından önce.

## Faz 6: Test

- Adım 6.1: `RaporServiceTest` (gerçek Postgres, `AbstractIntegrationTest` temelli) —
  `raporIcerigiOlustur()`'un doğru sayıları ve önceden eklenmiş audit kayıtlarını içerdiğini
  doğrula. Bu kısım projenin "gerçek DB, mock yok" ilkesine uyar.
- Adım 6.2: `raporGonder()` için **gerçek Gmail'e mail atılmayacak** — bu noktada projenin genel
  "mock yok" ilkesinden bilinçli bir sapma gerekiyor: ya `JavaMailSender`'ı test ortamında mock'la,
  ya da yerel/sahte bir SMTP sunucusu (örn. GreenMail, test-scope bağımlılık) kullan. Gerekçe:
  SMTP dış bir sistem, her test çalıştırmasında gerçek bir mail kutusuna mail düşürmek hem yanlış
  hem yavaş.
- Adım 6.3: `SecurityRulesIntegrationTest`'e `/api/raporlar/gonder`'ın VIEWER/EDITOR ile 403,
  ADMIN ile kabul edildiğini doğrulayan bir test ekle.

## Sırası Önemli Notlar

- Faz 0 gerçekten blocking — audit log'suz Faz 3 Adım 3.1'in ikinci yarısı (son 1 saatteki
  işlemler) yazılamaz.
- Faz 1 Adım 1.4 (Gmail app password üretimi) kullanıcı etkileşimi gerektirir, otomatik
  yapılamaz — bu adıma gelindiğinde kullanıcıdan bilgi istenecek.

## Tahmini Kapsam

Audit log tamamlandıktan sonra orta büyüklükte: yeni bağımlılık (mail starter), 2 yeni config
sınıfı (Async + Mail ayarları), 1 servis, 1 scheduler, 1 controller. Redis entegrasyonuyla
kıyaslanabilir boyutta.
