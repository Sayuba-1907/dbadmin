# PLAN: Kalıcı Audit Log İmplementasyonu

`requirement-audit-log.md`'nin uygulama planı.

## Faz 1: Entity ve Tablo

- Adım 1.1: `entity/AuditLog.java` oluştur — alanlar: `id`, `kullaniciId`, `kullaniciAdi` (denormalize,
  kullanıcı silinse bile okunabilir kalsın), `islemTipi` (enum: `TABLO_OLUSTURULDU`,
  `TABLO_SILINDI`, `KOLON_EKLENDI`, `KOLON_SILINDI`, `KOLON_YENIDEN_ADLANDIRILDI`,
  `SCHEMA_OLUSTURULDU`, `KULLANICI_ROLU_DEGISTIRILDI`, vb.), `hedefTip` (`TABLO`/`KOLON`/`SCHEMA`/
  `TAG`/`KULLANICI`), `hedefId`, `detay` (nullable, serbest metin), `traceId` (nullable),
  `olusturulmaZamani`.
- Adım 1.2: `repository/AuditLogRepository.java` — `JpaRepository` + filtreli bir `findAll` (Spring
  Data `Specification` ya da isim türetilmiş metodlar: kullanıcıya göre, hedef tipe göre, tarih
  aralığına göre).
- Adım 1.3: Hibernate `ddl-auto` neyse (muhtemelen `update` ya da migration dosyası varsa oraya)
  uygun şekilde tabloyu oluştur; `public` şemada, diğer metadata tabloları gibi.

## Faz 2: Yazma Mekanizması (Explicit, AOP Değil)

- Adım 2.1: `service/AuditLogService.java` oluştur — tek bir metod:
  `kaydet(IslemTipi tip, HedefTip hedefTip, Long hedefId, String detay)`. İçeride
  `SecurityContextHolder`'dan aktif kullanıcıyı okur (JWT ile zaten kimlikli), `AuditLog` satırını
  `save()` eder.
- Adım 2.2: **AOP/aspect ile otomatik yakalama yerine** (`RepositoryLoggingAspect` örneği var ama
  o sadece loglama, veri yazmıyor) her mutating servis metoduna elle `auditLogService.kaydet(...)`
  çağrısı eklenecek — Req-2.5/§0'daki "değiştirilemezlik ve görünürlük" tercihiyle tutarlı: hangi
  işlemin ne audit'lediği kod okunarak anlaşılabilsin, aspect'in hangi metodları yakaladığını
  ayrıca öğrenmeye gerek kalmasın.
- Adım 2.3: Çağrı noktaları — `TabloService.createTablo/deleteTablo/...`,
  `SchemaService.createSchema/updateSchema/deleteSchema`, `TagService.*`,
  `KullaniciService.changeRol/deleteKullanici/createKullanici`. Her biri kendi
  `@Transactional` metodun **içinde**, metadata/DDL yazımından sonra ama metod dönmeden önce
  çağrılacak — aynı transaction'ın parçası olması (Req-2.3) böylece garanti edilir.
- Adım 2.4: OTel implemente edildiyse (bkz. `plan-otel-implementation.md`), `traceId` alanı
  `Tracer.currentSpan()`'dan okunup dolsun; OTel yoksa `null` bırakılır — `AuditLogService` OTel'e
  derleme zamanı bağımlılık eklemeyecek (opsiyonel enjeksiyon veya `Optional<Tracer>`).

## Faz 3: Okuma Ucu

- Adım 3.1: `controller/AuditLogController.java` — `GET /api/audit-loglar`, `Pageable` parametreli,
  opsiyonel query parametreleri (`kullaniciId`, `hedefTip`, `bas`, `bit`).
- Adım 3.2: `SecurityConfig`'e `/api/audit-loglar/**` için `hasRole(ADMIN)` kuralı ekle —
  `/api/kullanicilar/**` kuralının hemen yanına, genel `/api/**` kuralından **önce** (sıralama
  kritik, mevcut `SecurityConfig` yorumundaki uyarıyla aynı sebep).
- Adım 3.3: `dto/AuditLogResponse.java` — entity'yi doğrudan dönmek yerine DTO (projedeki genel
  konvansiyon).

## Faz 4: Test

- Adım 4.1: Bir tablo oluşturma isteğinin hem `tablo`/`kolon` satırlarını hem gerçek DDL'i hem de
  bir `audit_log` satırını ürettiğini doğrulayan entegrasyon testi (`AbstractIntegrationTest`
  temelli, gerçek Postgres).
- Adım 4.2: Metadata/DDL yazımı sırasında bir hata enjekte edilirse (örn. geçersiz isim), audit
  satırının da yazılmadığını (transaction'ın tamamen rollback olduğunu) doğrulayan test — Req-3.1
  (fail-closed) burada kanıtlanır.
- Adım 4.3: `/api/audit-loglar` ucunun VIEWER/EDITOR rolüyle 403, ADMIN ile 200 döndüğünü
  doğrulayan `SecurityRulesIntegrationTest` benzeri bir test.

## Sırası Önemli Notlar

- Faz 2, OTel planından **bağımsız** ilerleyebilir — OTel önce bitmese de audit log tek başına
  çalışır (Req-3.6). Sıra kısıtı yok, hangisi önce ele alınırsa alınsın.
- Faz 2 Adım 2.3, kapsamı en çok genişleyen adım (6+ servis metoduna dokunuyor) — haftalık PR
  akışına uygun şekilde servis servis (önce `TabloService`, sonra `SchemaService`, ...) ayrı
  commit'lerle ilerlenebilir.

## Tahmini Kapsam

Redis'ten küçük, OTel'den küçük: tek yeni entity + repository + service + controller, mevcut
servislere birer satırlık çağrı ekleme. En büyük iş kalemi Faz 2 Adım 2.3'teki çağrı noktalarının
sayısı.
