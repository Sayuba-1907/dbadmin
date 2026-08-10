# GÖREV: Saatlik Otomatik Rapor Maili (Scheduler + Async)

"Daha ne yapabiliriz" tartışmasından çıkan, `@Scheduled`/`@Async` öğrenmeyi hedefleyen requirement.
**Ön koşulu var**: bkz. §3 Req-3.3 — audit log implementasyonu bu işten önce bitmiş olmalı.

## 1. Temel Amacımız (Epic / User Story)

Admin'e saatte bir, sistemin o anki durumunu (kaç şema/tablo/kolon var) ve son 1 saat içinde
yapılan işlemleri özetleyen bir e-posta raporu otomatik gönderilsin. Alıcı adres configurable
olsun (örn. bir Gmail hesabı). Mail gönderme işlemi, doğası gereği (ağ I/O, SMTP round-trip)
asenkron çalışsın — scheduler thread'i mail sunucusunu beklerken kilitlenmesin.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Saatlik tetikleme):** Rapor, her saat başı otomatik olarak oluşturulup gönderilecek
  (`@Scheduled(cron = "0 0 * * * *")`).
- **Req-2.2 (Rapor içeriği — iki bölüm):**
  - **Anlık görüntü**: o anki şema sayısı, tablo sayısı, kolon sayısı, kullanıcı sayısı.
  - **Son 1 saatteki işlemler**: `audit_log` tablosundan, `olusturulmaZamani` şimdiden 1 saat
    öncesine kadar olan kayıtlar — kim, ne yaptı, hangi hedefe (bkz. `requirement-audit-log.md`
    Req-2.2'deki alan yapısı).
- **Req-2.3 (Configurable alıcı):** Raporun gönderileceği e-posta adresi bir kullanıcı satırına
  değil, bir **yapılandırma değerine** bağlı olacak (Kullanici entity'sinde email alanı yok, bkz.
  plan §Faz 1) — `.env` üzerinden değiştirilebilir.
- **Req-2.4 (Manuel tetikleme):** `POST /api/raporlar/gonder` ucu eklenecek (ADMIN yetkisi) —
  saatte bir beklemeden raporu anında tetiklemek, geliştirme/test sırasında ve gerçek kullanımda
  "şimdi bir rapor istiyorum" ihtiyacı için.
- **Req-2.5 (Async gönderim):** Mail gönderme adımı `@Async` bir metotta çalışacak; rapor
  içeriğini oluşturma (DB sorguları) senkron kalabilir, sadece SMTP'ye bağlanıp mail atma adımı
  asenkron olacak — asıl beklemeyi gerektiren kısım o.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Özel thread pool, varsayılana güvenilmeyecek):** Spring'in `@Async`'i, elle bir
  `TaskExecutor` bean'i tanımlanmazsa varsayılan olarak `SimpleAsyncTaskExecutor` kullanır — bu
  havuzlama yapmaz, her çağrıda yeni bir thread açar. Saatte bir tetiklenen bir iş için pratikte
  zararsız olsa da, bilerek küçük sabit boyutlu bir `ThreadPoolTaskExecutor` (örn. core=1, max=2)
  tanımlanacak; "varsayılanı sorgulamadan kullanma" ilkesi Redis'teki 60sn timeout gotcha'sıyla
  aynı gerekçeye dayanıyor.
- **Req-3.2 (Fail-open):** SMTP sunucusuna erişilemezse, kimlik doğrulama hatası alınırsa veya mail
  gönderimi başka bir sebeple başarısız olursa uygulama çökmeyecek; hata ERROR seviyesinde
  loglanacak ve **ayrıca retry mekanizması yazılmayacak** — bir sonraki saatlik çalışma zaten
  doğal bir yeniden deneme sağlıyor. Redis cache'in fail-open felsefesiyle aynı: raporlama bir
  bağımlılık değil, bir ek özelliktir.
- **Req-3.3 (Ön koşul — audit log):** Bu özellik `requirement-audit-log.md`'nin implementasyonuna
  bağımlıdır; `audit_log` tablosu ve `AuditLogRepository` olmadan Req-2.2'nin "son 1 saatteki
  işlemler" bölümü doldurulamaz. Sıralama: **önce audit log, sonra bu iş.**
- **Req-3.4 (Hassas veri sızmasın):** Mail içeriğinde parola hash'i, JWT secret'ı gibi hassas
  veriler yer almayacak — audit log zaten bunları tutmuyor (bkz. `requirement-audit-log.md`
  Req-3.3), rapor da sadece isim/id/işlem tipi gibi tanımlayıcı bilgi taşıyacak.
- **Req-3.5 (Yapılandırma, sır .env'de):** SMTP host/port/kullanıcı adı/parola (Gmail için "uygulama
  şifresi" — normal Gmail şifresiyle SMTP girişi artık engelleniyor) ve alıcı admin e-posta adresi
  `.env` üzerinden `docker-compose.yml`'e environment değişkeni olarak geçirilecek; JWT secret ve
  admin parolasıyla aynı pattern — koda veya compose dosyasına sabit yazılmayacak.
- **Req-3.6 (Zaman dilimi):** Cron ifadesi container'ın çalıştığı saat dilimine göre yorumlanır;
  bu bilerek not düşülüyor ki "saat başı" beklenen zamanda tetiklenmediğinde ilk bakılacak yer
  burası olsun.
- **Req-3.7 (Test edilebilirlik):** Rapor oluşturma ve mail gönderme mantığı, `@Scheduled`'dan
  bağımsız, sade bir serviste (`RaporService`) yaşayacak — hem `@Scheduled` metodu hem manuel
  tetikleme ucu (Req-2.4) hem testler aynı servisi çağırabilsin.

## 4. Kapsam Dışı (Bu Aşamada Yapılmayacak)

- Rapor formatının HTML şablonlaştırılması — ilk sürüm düz metin mail olacak.
- Birden fazla alıcı veya farklı sıklıklarda (günlük/haftalık) farklı raporlar.
- Mail gönderiminin ayrı bir retry/backoff mekanizması (Req-3.2'de gerekçesiyle bilerek dışarıda
  bırakıldı).

## 5. Durum

İmplemente edildi: `ReportScheduler` (saatlik cron), `ReportService` (içerik + async gönderim),
`ReportController` (`POST /api/reports/send`, ADMIN), özel `reportTaskExecutor` (`AsyncConfig`).
Testler: rapor içeriği (gerçek Postgres), mail gönderimi (mock `JavaMailSender`), fail-open, ve
security rules (VIEWER/EDITOR 403, ADMIN 202) — hepsi yeşil. Uygulama planı için
`plan-scheduler-report-implementation.md`'ye bakınız.
