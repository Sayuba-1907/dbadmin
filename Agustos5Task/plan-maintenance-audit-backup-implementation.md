# PLAN: Maintenance Sayfası — Sistem Özeti + Audit Log Yedekleme (MinIO)

`requirement-maintenance-audit-backup.md`'nin uygulama planı. Ön koşul (`requirement-audit-log.md`)
2026-08-06'da tamamlandı: `AuditLog` entity, `AuditLogRepository`, `AuditLogService`,
`GET /api/audit-logs` (ADMIN) hazır — bu plan bunların üzerine inşa ediyor, sıfırdan yazmıyor.

## Faz 0: Mevcut Kod Taraması (bulgular, uygulama sırasında referans için)

- `ReportService.buildReportContent()` (`service/ReportService.java`), şema/tablo/kolon/kullanıcı
  sayısını `schemaRepository.count()` / `tableRepository.count()` / `columnRepository.count()` /
  `userRepository.count()` ile hesaplıyor — Req-2.1 bu dört çağrının **aynısını** yeni bir
  endpoint'ten sunacak, `ReportService`'i değiştirmeden.
- `AuditLogRepository extends JpaRepository<AuditLog, Long>` — id tipi `Long`. `search(...)` dışında
  bir metod yok; cutoff-id akışı için yeni metodlar eklenecek (bkz. Faz 2).
- `AuditLogService.currentUsername()` **private** — "kim yedek aldı" bilgisi için bu metod `public`
  yapılıp yeni backup servisinden çağrılacak (SYSTEM_USER fallback semantiğini tekrar yazmamak
  için), `record(...)` imzası değişmeyecek.
- `SecurityConfig`'te ADMIN-only kurallar `/api/users/**`, `/api/audit-logs/**`, `/api/reports/**`
  şeklinde, genel `/api/**` kurallarından **önce** sıralanmış (satır 86-89) — yeni
  `/api/maintenance/**` kuralı aynı bloğa eklenecek.
- Secret'lar `application.properties`'te `${ENV_VAR:default}` deseniyle geçiliyor
  (`app.jwt.secret`, `spring.mail.username/password`, `app.report.admin-email`) — MinIO için aynı
  desen (`app.minio.*`) kullanılacak, hiçbir secret compose'a sabit yazılmayacak.
- `spring-boot-starter-actuator` zaten dependency, `management.endpoints.web.exposure.include=
  health,metrics,prometheus` açık; Postgres + Redis health'i **otomatik** `/actuator/health`'te
  var (`spring-data-redis` ve DataSource classpath'te olduğu için Spring Boot kendi
  health contributor'larını üretiyor) — Req-2.2'nin Postgres/Redis kısmı için sıfırdan kod
  gerekmiyor, sadece `/actuator/health`'in ilgili component'i okunacak. Tempo/Loki için Spring'in
  otomatik ürettiği bir contributor yok — bunlar için `/ready` uçlarına (Tempo `:3200/ready`,
  Loki `:3100/ready`) basit bir HTTP GET yazılacak.
- `pom.xml`'de S3/MinIO SDK yok — `io.minio:minio` eklenecek (AWS SDK'dan daha basit, tek işimiz
  put-object).
- Frontend'de router yok, `Dashboard.tsx`'teki `activeView` state + `WorkspaceNav.tsx`'teki
  `{isAdmin && (...)}` deseni (Users sekmesiyle aynı) yeni "Maintenance" sekmesi için tekrar
  edilecek. Liste ucu tüketimi için `UsersPanel.tsx` + `useUsers.ts` + `api/users.ts` üçlüsü
  referans alınacak (auto-fetch yok, sekme açılınca fetch).

## Faz 1: MinIO Altyapısı

- Adım 1.1: `docker-compose.yml`'e `minio` servisi ekle — `redis` bloğuyla aynı seviyede
  (image `minio/minio`, `command: server /data --console-address ":9001"`, healthcheck
  `curl -f http://localhost:9000/minio/health/live`, named volume `minio_data:/data`,
  `dbadmin-net`, `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` env'leri `.env`'den). Console portu
  (9001) sadece local debug için `expose` değil `ports` ile açılabilir (Grafana/Redisinsight gibi).
- Adım 1.2: `.env`'e `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `APP_MINIO_ACCESS_KEY`,
  `APP_MINIO_SECRET_KEY`, `APP_MINIO_BUCKET=audit-log-backups` ekle — ilk ikisi container'ın kendi
  kimliği, son ikisi backend'in bu container'a bağlanırken kullanacağı kimlik (basit kurulumda
  aynı değer olabilir, MinIO tek kullanıcı/tek access-key modeliyle çalışır; ayrı bir
  access-key/secret-key çifti istenirse `mc admin user add` ile ek adım gerekir — kapsam dışı,
  root credential'ları kullanmak bu ölçekte yeterli).
- Adım 1.3: `backend` servisinin `environment` bloğuna `APP_MINIO_ENDPOINT=http://minio:9000`,
  `APP_MINIO_ACCESS_KEY`, `APP_MINIO_SECRET_KEY`, `APP_MINIO_BUCKET` eklenir; `depends_on`'a
  `minio: condition: service_healthy` eklenir (redis'teki desenle aynı).
- Adım 1.4: `application.properties`'e karşılık gelen `app.minio.endpoint/access-key/secret-key/
  bucket=${...:...}` satırları eklenir (local `mvnw spring-boot:run` için endpoint default'u
  `http://localhost:9000`).
- Adım 1.5: `pom.xml`'e `io.minio:minio` dependency eklenir.
- Adım 1.6: `config/MinioConfig.java` — tek bir `@Bean MinioClient` (endpoint + access/secret key
  ile), uygulama açılışında bucket yoksa oluşturan bir `@PostConstruct`/`ApplicationRunner`
  (`bucketExists` + `makeBucket`) — Redis/Postgres gibi dışarıdan hazır gelmesi beklenmeyen tek
  altyapı parçası bu, ilk açılışta bucket'ın var olduğunu garanti etmek gerekiyor.

## Faz 2: Backend — Audit Log Yedekleme

- Adım 2.1: `AuditLogRepository`'ye iki metod ekle:
  `List<AuditLog> findAllByOrderByIdAsc()` (tüm tabloyu id sırasıyla oku — Req-2.4.1) ve
  `long deleteByIdLessThanEqual(Long cutoffId)` (Req-2.5, `@Modifying` + `@Transactional` gerekir,
  Spring Data derived delete deseni).
- Adım 2.2: `AuditLogService.currentUsername()`'ı `public` yap (private'tan) — Adım 2.4'teki backup
  servisinin "kim yedekledi" bilgisini aynı fallback mantığıyla (SYSTEM_USER) okuyabilmesi için.
- Adım 2.3: `service/MinioService.java` — tek metod: `void upload(String key, byte[] content,
  String contentType)`, `MinioClient.putObject(...)` çağırır, hata durumunda checked exception'ı
  unchecked bir `BackupFailedException`'a (yeni, `exception/`'de) sarıp fırlatır — üst katmanın
  fail-closed davranabilmesi için (Req-3.3).
- Adım 2.4: `service/AuditLogBackupService.java` — tek metod `BackupResult backup()`:
  1. `List<AuditLog> rows = auditLogRepository.findAllByOrderByIdAsc()`; boşsa (0 satır)
     `ValidationException` ile erken dön — yedeklenecek bir şey yokken boş bir dosya MinIO'ya
     yazıp "başarılı" demenin bir anlamı yok, kullanıcıya net bir mesaj daha faydalı.
  2. `cutoffId = rows.get(rows.size() - 1).getId()`.
  3. Her satırı `id` **hariç** bir DTO'ya çevir (`AuditLogBackupEntryDto`: userId, username,
     operationType, targetType, targetId, detail, traceId, createdAt) — Req-2.4.2.
  4. Meta blok oluştur (`AuditLogBackupMetaDto`: `backedUpBy` = `auditLogService.currentUsername()`,
     `backedUpAt` = `Instant.now()`, `rowCount` = `rows.size()`) — Req-2.4.3, **bir `AuditLog`
     satırı olarak yazılmaz**, sadece dosyanın içine gömülür.
  5. `{ "meta": {...}, "entries": [...] }` şeklinde tek bir JSON'a serialize et (Jackson
     `ObjectMapper`, projede zaten Spring Boot ile geliyor).
  6. `String key = "backup-" + DateTimeFormatter ile "yyyy-MM-dd'T'HH-mm-ss'Z'" formatında UTC
     zaman damgası + ".json"`.
  7. `minioService.upload(key, jsonBytes, "application/json")` — **bu adım metod içinde en önce
     DB'ye dokunmadan önce** çalışır (Req-3.2, sıra kritik).
  8. Upload başarılıysa (exception fırlamadıysa) `auditLogRepository.deleteByIdLessThanEqual
     (cutoffId)` çağrılır — Req-2.5, blanket delete değil.
  9. Upload adım 7'de exception fırlatırsa metod da fırlatır, adım 8 hiç çalışmaz, DB'ye
     dokunulmamış olur (Req-3.3, fail-closed) — `@Transactional` metod olsa bile MinIO
     transaction'a dahil olmadığı için ekstra bir catch/rollback mantığı **gerekmiyor**, sıra
     zaten yeterli (Req-3.2'nin "atomicity değil ama güvenlik" ayrımı burada).
  - Not: adım 1 ile adım 7 arasında yeni bir audit satırı yazılırsa (concurrent kullanıcı işlemi),
    o satırın id'si `cutoffId`'den büyük olacağı için adım 8'deki `<= cutoffId` silmesi ona
    dokunmaz — bir sonraki yedeklemede o satır da dahil edilir (Req-2.5'in gerekçesi budur).
- Adım 2.5: `controller/MaintenanceController.java`'ye `POST /api/maintenance/audit-logs/backup`
  eklenir (Req-2.4) — `AuditLogBackupService.backup()`'ı çağırır, `BackupResult`'ı (rowCount,
  key, backedUpAt) 200 ile döner. Endpoint audit log okuma ucunun (`/api/audit-logs`) yanına değil
  `/api/maintenance/**` altına konur çünkü Req-3.4 yeni bir prefix'i tek seferde ADMIN'e kilitliyor.

## Faz 3: Backend — Sistem Özeti + Servis Sağlığı

- Adım 3.1: `dto/SystemSummaryResponse.java` — `schemaCount`, `tableCount`, `columnCount`,
  `userCount` (Req-2.1, `ReportService.buildReportContent()`'teki dört `.count()` çağrısının
  aynısı).
- Adım 3.2: `dto/ServiceHealthResponse.java` — `Map<String, Boolean>` ya da sabit alanlı
  (`postgres`, `redis`, `tempo`, `loki`) bir DTO (Req-2.2).
- Adım 3.3: `service/MaintenanceService.java` — iki metod:
  - `SystemSummaryResponse systemSummary()`: `SchemaRepository`/`TableRepository`/
    `ColumnRepository`/`UserRepository` inject edilir (mevcut `ReportService`'teki dörtlüyle aynı),
    `.count()` çağrılır. `ReportService`'e yeni bir bağımlılık **eklenmez** — iki servis aynı
    repository'lere bağımsızca bağımlı olur, `ReportService`'in rapor-gönderme sorumluluğuyla
    maintenance sayfasının okuma sorumluluğu karışmaz.
  - `ServiceHealthResponse serviceHealth()`: Postgres/Redis için Spring Boot Actuator'ın
    `HealthContributorRegistry`'sinden (`org.springframework.boot.actuate.health`) `db` ve `redis`
    contributor'larını okur (constructor injection, `HealthContributorRegistry` bean'i actuator
    classpath'te olduğu için hazır); Tempo/Loki için `RestClient` ile `GET :3200/ready` /
    `GET :3100/ready` atar, 2xx dönerse `true`, timeout/hata durumunda (kısa bir timeout,
    ör. 2 saniye, sayfa MinIO/DB gibi kritik bir işlem değil) `false` — hiçbir durumda exception
    yukarı çıkmaz, hepsi `true`/`false`'a normalize edilir (Req-2.2 "basit yeşil/kırmızı gösterge").
- Adım 3.4: `controller/MaintenanceController.java`'ye (Adım 2.5'teki controller'ın içine)
  `GET /api/maintenance/summary` ve `GET /api/maintenance/health` eklenir.

## Faz 4: Yetkilendirme

- Adım 4.1: `SecurityConfig`'teki ADMIN-only blok'a (satır 86-89 civarı, `/api/users/**` /
  `/api/audit-logs/**` / `/api/reports/**`'in yanına) `.requestMatchers("/api/maintenance/**")
  .hasRole(Role.ADMIN.name())` eklenir — genel `/api/**` kurallarından önce olacak şekilde,
  mevcut sıralama korunur. Javadoc yorum bloğu güncellenir.

## Faz 5: Frontend

- Adım 5.1: `api/maintenance.ts` — `getSystemSummary()`, `getServiceHealth()`,
  `backupAuditLogs()` (`apiGet`/`apiPost` üzerinden, `api/client.ts` deseniyle).
- Adım 5.2: `hooks/useMaintenance.ts` — `useUsers.ts` deseniyle (auto-fetch yok, sekme açılınca
  `refresh()`, `useCallback`, hata yutulmaz).
- Adım 5.3: `components/MaintenancePanel.tsx` — iki bölüm: (A) özet kartları + sağlık göstergeleri
  (Req-2.1/2.2), (B) `GET /api/audit-logs` ile beslenen filtrelenebilir tablo (mevcut endpoint,
  Req-2.3 — yeni bir backend ucu gerekmez, sadece frontend'de tabloya bağlanır) + sağ üstte
  "Yedekle" butonu (Adım 2.5'teki ucu çağırır, başarı/hata toast'ı gösterir, başarılıysa tabloyu
  `refresh()` eder ki temizlenen satırlar ekrandan da kalksın).
- Adım 5.4: `WorkspaceNav.tsx`'e Users'la aynı desende `{isAdmin && (...)}` ile "Maintenance"
  sekmesi eklenir; `Dashboard.tsx`'teki `activeView`/`WorkspaceView` union tipine yeni değer
  eklenir ve ilgili `else if` dalı `MaintenancePanel`'i render eder.

## Faz 6: Test

- Adım 6.1: `AuditLogBackupServiceIntegrationTest` (`AbstractIntegrationTest` temelli, gerçek
  Postgres + gerçek MinIO — Testcontainers'a MinIO container eklenebilir ya da compose'daki MinIO
  test profilinde de ayağa kaldırılabilir, ikisinden biri seçilecek): birkaç audit satırı
  oluştur → `backup()` çağır → (a) MinIO'da beklenen key altında dosya var ve içeriği doğru
  (id yok, meta blok var, satır sayısı doğru) (b) `audit_log` tablosunda cutoff'a kadar olan
  satırlar silinmiş (c) backup sırasında/sonrasında eklenen yeni bir satır silinmemiş (cutoff-id
  senaryosunu simüle eden test, Req-2.5'in asıl sebebi).
- Adım 6.2: MinIO upload'ı hata fırlatacak şekilde enjekte edilirse (ör. yanlış bucket/erişilemez
  endpoint ile bir test double) `audit_log` tablosunun **hiç** değişmediğini doğrulayan test
  (Req-3.3, fail-closed).
- Adım 6.3: `POST /api/maintenance/audit-logs/backup`, `GET /api/maintenance/summary`,
  `GET /api/maintenance/health` uçlarının VIEWER/EDITOR ile 403, ADMIN ile 200 döndüğünü
  doğrulayan `SecurityRulesIntegrationTest` genişletmesi.
- Adım 6.4: Playwright — Maintenance sekmesinin sadece ADMIN girişinde göründüğünü, "Yedekle"
  butonuna basınca tablonun boşaldığını (ya da en azından değiştiğini) doğrulayan bir e2e senaryo
  (notlar dosyasındaki "Ekranda gördüğün her butona tıkla" talimatıyla uyumlu).

## Sırası Önemli Notlar

- Faz 1 (MinIO altyapısı) Faz 2'nin **önkoşulu** — `MinioClient` bean'i olmadan
  `AuditLogBackupService` yazılamaz.
- Faz 3 (özet + health), Faz 1/2'den **bağımsız** ilerleyebilir — istenirse önce bu yapılıp ayrı
  bir commit/PR parçası olarak bitirilebilir (haftalık PR akışına uygun, Req'te de A/B bölümleri
  ayrı ele alınmış).
- Faz 5 (frontend), Faz 2-4 bitmeden başlanabilir (mock data ile) ama gerçek entegrasyon testi
  backend uçları hazır olmadan yapılamaz.
- MinIO container ilk `docker compose up`'ta boş bir volume ile başlayacağı için Adım 1.6'daki
  bucket-oluşturma adımı atlanırsa ilk backup denemesi `NoSuchBucketException` ile patlar —
  bu adım gözden kaçırılmaya müsait, implementasyonda özellikle test edilecek.

## Tahmini Kapsam

Redis'ten biraz büyük (yeni bir altyapı servisi + yeni bir SDK dependency + Docker Compose
değişikliği içerdiği için), audit-log implementasyonuyla benzer büyüklükte: 1 yeni Docker servisi,
1 yeni SDK dependency, ~4 yeni backend sınıfı (`MinioConfig`, `MinioService`,
`AuditLogBackupService`, `MaintenanceService`) + 1 controller + birkaç DTO, `AuditLogRepository`'ye
2 metod, `SecurityConfig`'e 1 satır, frontend'de 1 yeni panel + hook + api dosyası + nav girişi.
