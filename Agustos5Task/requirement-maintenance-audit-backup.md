# GÖREV: Maintenance Sayfası — Sistem Özeti + Audit Log Yedekleme (MinIO)

"Minio'yu ne için kullanabiliriz" tartışmasından çıkan, MinIO/object storage öğrenmeyi hedefleyen
requirement. **Ön koşulu var**: `requirement-audit-log.md` implementasyonu bitmiş olmalı —
`audit_log` tablosu ve `AuditLogRepository` olmadan bu iş yapılamaz.

## 1. Temel Amacımız (Epic / User Story)

ADMIN'e özel, tek bir "Maintenance" sayfası eklenecek. Sayfa iki bölümden oluşacak: (A) sistemin
anlık durumunu ve bağımlı servislerin sağlığını gösteren bir özet, (B) audit log kayıtlarını
filtreleyerek listeleyen ve istenildiğinde tamamını bir JSON dosyası olarak MinIO'ya yedekleyip
tabloyu temizleyen bir bölüm.

## 2. İşlevsel Gereksinimler (Functional Requirements)

### A) Sistem özeti + servis sağlığı

- **Req-2.1 (Anlık durum kartları):** Şema sayısı, tablo sayısı, kolon sayısı, kullanıcı sayısı —
  `ReportService.buildReportContent()`'te zaten hesaplanan mantığın aynısı, ayrı bir endpoint
  üzerinden frontend'e taşınacak.
- **Req-2.2 (Servis sağlık durumu):** Postgres, Redis, Tempo, Loki için basit bir erişilebilirlik
  göstergesi (yeşil/kırmızı gibi) — her biri için ayrı, ucuz bir health check.

### B) Audit Log — listeleme ve yedekleme

- **Req-2.3 (Filtrelenebilir liste):** Mevcut `GET /api/audit-logs` ucu (userId, targetType,
  targetId, from, to filtreleri + sayfalama) frontend'de bir tabloya bağlanacak. Backend'de yeni
  bir sorgu ucu gerekmiyor.
- **Req-2.4 (Yedekle butonu):** Sayfanın audit log bölümünde, sağ üstte bir "Yedekle" butonu
  olacak (`POST /api/audit-logs/backup`, ADMIN). Tetiklendiğinde:
  1. O ana kadarki **tüm** `audit_log` satırları okunur.
  2. Her satır JSON'a dönüştürülür — **`id` alanı dahil edilmez** (DB'ye özgü, sequence
     kaynaklı bir değer; dosyaya taşındıktan sonra iş anlamı taşımıyor). `userId`, `username`,
     `operationType`, `targetType`, `targetId`, `detail`, `traceId`, `createdAt` kalır.
  3. Dosyanın içine ayrıca bir **meta blok** eklenir: yedeği kim aldı (kullanıcı adı), ne zaman
     aldı (timestamp), kaç satır yedeklendi. Bu bilgi bir `AuditLog` satırı olarak **DB'ye
     yazılmaz** — sadece dosyanın içine gömülür; aksi halde bir sonraki adımda tablo temizlenince
     "kim temizledi" bilgisi de silinmiş olurdu.
  4. Dosya MinIO'ya yüklenir (bucket: `audit-log-backups`, key: zaman damgalı, ör.
     `backup-2026-08-10T11-31-00Z.json`).
- **Req-2.5 (Yükleme başarılıysa temizlik — cutoff'a göre, tüm tabloya göre değil):** MinIO'ya
  yükleme başarıyla tamamlandıktan **sonra**, yedeklenen satırlar `audit_log` tablosundan silinir.
  Silme, adım 1'de okunan **en yüksek `id`'ye kadar** (`DELETE WHERE id <= cutoffId`) yapılır —
  blanket `DELETE FROM audit_log` **değil**. Sebep: adım 1 (satırları oku) ile bu adım arasında
  geçen sürede (JSON oluşturma + MinIO'ya network upload — göz ardı edilebilir kısa bir an değil)
  başka bir kullanıcının işlemi yeni bir audit satırı yazabilir; cutoff olmadan yapılan bir
  "tüm satırları sil" bu satırı da silip hiç yedeklenmemiş bir kaydı kaybettirir. Yükleme herhangi
  bir sebeple başarısız olursa tabloya dokunulmaz, hata döner — CLAUDE.md'deki dual-write
  ilkesiyle aynı: iki yazımdan biri (MinIO) başarısızken diğerine (DB silme) geçilmez.
- **Req-2.6 (Yedeklere erişim):** Yedek dosyalarını listeleyen/indiren ayrı bir ekran bu aşamada
  **yapılmayacak** (bkz. §4) — dosyalara MinIO console üzerinden bakılacak.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (MinIO altyapısı):** `docker-compose.yml`'e yeni bir `minio` servisi eklenecek
  (Redis/Grafana ile aynı seviyede). Access key/secret key ve bucket adı `.env` üzerinden
  geçirilecek — JWT secret ve SMTP kimlik bilgileriyle aynı pattern, koda/compose'a sabit
  yazılmayacak.
- **Req-3.2 (Sıra, atomicity değil ama güvenlik):** MinIO nesne yazımı ile Postgres silme işlemi
  tek bir transaction'da birleştirilemez (biri object storage, biri DB) — bu yüzden sıra kritik:
  önce MinIO'ya yaz, yükleme response'u başarıyı doğrulamadan DB'ye dokunma (Req-2.5).
- **Req-3.3 (Fail-closed):** `AuditLogService`'teki mevcut fail-closed felsefesiyle tutarlı:
  MinIO yüklemesi başarısız olursa exception yukarı çıkar, DB silme adımı hiç çalışmaz, kullanıcıya
  hata döner. Redis/rapor gönderimindeki fail-open'ın tersine, burası bir cache/rapor değil,
  "gerçek yedek" olduğu için hata sessizce yutulmayacak.
- **Req-3.4 (Yetkilendirme):** Hem sayfa (frontend route) hem `POST /api/audit-logs/backup` hem
  yeni sağlık/özet endpoint'leri **sadece ADMIN** rolüne açık — mevcut `/api/audit-logs/**`,
  `/api/users/**` kurallarıyla aynı seviyede `SecurityConfig`'e eklenecek.
- **Req-3.5 (Dosya formatı):** Tek bir JSON dosyası (satırların hepsini içeren bir array + meta
  blok) — periyodik/parça parça append değil, tek seferlik toplu dump. S3/MinIO nesneleri append
  edilemediği için bu formatı seçtik; sürekli küçük parçalar halinde yazma bu aşamada kapsam dışı.

## 4. Kapsam Dışı (Bu Aşamada Yapılmayacak)

- Yedeklerin frontend'den listelenmesi/indirilmesi (Req-2.6) — MinIO console yeterli.
- Otomatik/zamanlanmış yedekleme (`@Scheduled`) — sadece buton ile manuel tetikleme.
- Kısmi yedekleme (ör. sadece belirli tarih aralığını yedekleyip geri kalanını DB'de bırakma) —
  her seferinde **tüm** tablo yedeklenip temizlenir.
- Restore (MinIO'daki bir yedeği tekrar DB'ye geri yükleme) uç noktası.
- Sistem özeti/health check için ayrı bir metrik/alerting sistemi — Prometheus/Grafana zaten var,
  burası sadece hızlı bir görsel özet.

## 5. Durum

2026-08-10'da implemente edildi (backend/week4): `plan-maintenance-audit-backup-implementation.md`'deki
Faz 1-6 tamamlandı — MinIO servisi (docker-compose + `.env`), `MinioClient`/`MinioBucketInitializer`,
`AuditLogBackupService` (önce MinIO'ya yaz, sonra cutoff-id'ye göre sil), `MaintenanceService`
(sistem özeti + Postgres/Redis/Tempo/Loki sağlık göstergesi), `MaintenanceController`
(`GET /api/maintenance/summary`, `GET /api/maintenance/health`,
`POST /api/maintenance/audit-logs/backup`, hepsi ADMIN), frontend `MaintenancePanel` (özet
kartları, sağlık rozetleri, filtrelenebilir audit log tablosu, "Yedekle" butonu), backend
entegrasyon testleri (cutoff-id garantisi, fail-closed, yetki) ve Playwright e2e testi. Yol
boyunca bulunan/çözülen sorunlar için bkz. `DECISIONS.md`.
