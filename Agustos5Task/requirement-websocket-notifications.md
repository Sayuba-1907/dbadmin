# GÖREV: Tablo Sahipliği + Canlı Bildirim Sistemi (WebSocket)

"Frontend için öğretici bir şey" tartışmasından çıkan, WebSocket'i öğrenmeyi hedefleyen requirement.
İlk fikir ("başka bir admin bir şey değiştirdiğinde herkese bildirim") beğenilmedi; bunun yerine
**tablo sahipliği** temelli, hem popup hem kalıcı bildirim merkezi olan bu tasarıma karar verildi.

## 0. Öğrenme Hedefi ve Genel Kullanım Alanı

**Bu görevde asıl öğrenilecek şey**: HTTP'nin istek-cevap modelinden farklı olarak, WebSocket
sunucunun **istemciyi beklemeden, kendi inisiyatifiyle** veri gönderebildiği (push) kalıcı bir
bağlantı sağlar. Bu projede bunun somut karşılığı: bildirim sayacı **hiçbir zaman sunucuya
sorulmaz** (polling yok) — sunucu bir bildirim oluştuğu anda ilgili kullanıcının açık bağlantısına
kendiliğinden yazar, istemci sadece dinler ve yerel state'ini günceller.

**İkinci öğrenilecek kavram**: broadcast (herkese yayın) ile **targeted/hedefli mesajlaşma**
arasındaki fark. İlk WebSocket fikrimiz (herkese "bir şey değişti" yayını) basitti ama gerçek
dünyadaki bildirim sistemlerinin çoğu (Gmail, Slack, GitHub'daki "3 yeni bildirim" rozeti) hedefli
çalışır — mesaj sadece ilgili kullanıcıya gider. Bunu yapmak için backend'in **hangi WebSocket
session'ın hangi kullanıcıya ait olduğunu bilmesi** gerekir (`kullaniciId → session` eşlemesi),
bu da JWT tabanlı kimlik doğrulamayı WebSocket handshake'ine taşımayı gerektiriyor — projenin
zaten sahip olduğu JWT mekanizmasının yeni bir bağlamda tekrar kullanılması.

**Genel kullanım alanı (bu projenin dışında):** Her "zil ikonu + sayaç" gördüğünüz üründe
(e-posta istemcileri, sosyal medya, proje yönetim araçları) bu tam olarak aynı mekanizmadır —
sunucu tarafı push + istemci tarafı yerel state güncelleme. Aynı desen canlı sohbet, ortak
düzenleme (Google Docs'ta "şu an kim yazıyor"), borsa/kripto anlık fiyat güncellemeleri gibi çok
farklı ürünlerde de temel taşıdır.

## 1. Temel Amacımız (Epic / User Story)

Her tablonun bir **sahibi** (oluşturan kullanıcı) olsun. Sahibi olmayan biri o tabloyu etkileyen
bir işlem yaptığında, sahibe hem anlık bir popup (o an bağlıysa) hem kalıcı bir bildirim (bildirim
merkezinde, bağlı olmasa bile sonradan görülebilir) düşsün. Ön yüzdeki bildirim ikonundaki sayaç,
sunucudan sürekli sorulmadan, WebSocket push'uyla anlık güncellensin.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Tablo sahipliği):** `Tablo` entity'sine `olusturanKullaniciId` eklenecek;
  `TabloService.createTablo` bunu aktif kullanıcıdan (`SecurityContextHolder`) doldurur.
- **Req-2.2 (Tetikleyici kapsam — geniş):** Tabloyu etkileyen **her** mutasyon — kolon
  ekle/sil/rename/tag ata/primary-key değiştir, tablo rename, schema değiştir, tablo silme
  (`Tablo.touch()`'ın çağrıldığı her yer) — eğer işlemi yapan kullanıcı sahibi değilse bir
  bildirim üretir.
- **Req-2.3 (Kalıcı bildirim kaydı):** Yeni bir `Bildirim` tablosu: alıcı (`aliciKullaniciId`),
  hedef tablo (`tabloId` + `tabloAdi`, tablo silinse bile okunabilir kalsın diye denormalize),
  tetikleyen kullanıcı adı, işlem türü, mesaj, `okunduMu` (bu alan **audit log'dan farklı olarak**
  değiştirilebilir — bkz. Req-3.5), oluşturulma zamanı.
- **Req-2.4 (Anlık push):** Sahibin WebSocket bağlantısı açıksa, bildirim oluşturulduğu anda
  hedefli (sadece o kullanıcıya) bir mesaj gönderilir. Frontend bunu alınca sayaç ve listeyi
  **yerelde** günceller, sunucuya tekrar sormaz.
- **Req-2.5 (İlk yükleme, tek seferlik):** Sayfa açıldığında/WebSocket bağlantısı ilk kurulduğunda
  **bir kez** `GET /api/bildirimler/okunmamis-sayisi` çağrılır — kullanıcı offline'ken oluşmuş
  bildirimler böylece kaçırılmaz. Bundan sonra sayaç sadece push ile değişir.
- **Req-2.6 (Bildirim merkezi):** `GET /api/bildirimler` — sayfalı, en yeni önce, sadece
  isteyenin **kendi** bildirimlerini döner.
- **Req-2.7 (Okundu işaretleme):** `PATCH /api/bildirimler/{id}/okundu` (tekil) ve
  `PATCH /api/bildirimler/okundu` (tümünü okundu işaretle).
- **Req-2.8 (Popup):** Aktif oturumda WebSocket'ten bir bildirim geldiğinde, mevcut
  `NotificationProvider` altyapısı (`notify(...)`) ile toast gösterilir — yeni bir bildirim UI
  mekanizması yazılmaz, var olan reuse edilir.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Targeted WebSocket mimarisi):** Broadcast-to-everyone değil; backend'de
  `kullaniciId → WebSocketSession(ler)` registry tutulacak (bir kullanıcı birden fazla
  sekme/cihazdan bağlanabileceği için kullanıcı başına birden fazla session olabilir). JWT,
  handshake'te query param olarak taşınacak (tarayıcı native `WebSocket` API'si custom header
  desteklemiyor) — bu, query param'ların loglara/proxy'lere sızma riskini taşıdığı bilinen ve
  kabul edilen bir trade-off (kısa ömürlü JWT + öğrenme ortamı).
- **Req-3.2 (Geriye dönük owner — backfill):** Owner alanı eklendiğinde mevcut tablolar için
  bir backfill adımı çalışacak ve hepsi **ilk ADMIN kullanıcıya** atanacak (bilinçli varsayım:
  gerçekte o tabloyu admin oluşturmamış olabilir, ama alan zorunlu hale getirilmeden önce bir
  değer gerekiyor).
- **Req-3.3 (Kendi kendine bildirim yok):** Aktif kullanıcı == tablo sahibi ise bildirim
  **üretilmez** — kimse kendi yaptığı değişiklik için kendine bildirim almaz.
- **Req-3.4 (Fail-open push, fail-closed kayıt):** Bildirim DB satırı audit log ile aynı ilkeyle
  (aynı transaction, rollback olursa o da rollback olur) yazılır — bu kısım **fail-closed**. Ama
  WebSocket push'un kendisi (Req-2.4) best-effort'tur: session kapalıysa/bulunamazsa push
  atlanır, hata fırlatılmaz — kullanıcı sonraki girişte ilk-yükleme sayacından (Req-2.5) zaten
  görecektir. Redis'teki fail-open felsefesiyle aynı: anlık bildirim bir garanti değil, bir
  optimizasyondur; kalıcı kayıt (DB satırı) garantidir.
- **Req-3.5 (Commit sonrası push — kritik detay):** Bildirim satırı `@Transactional` metodun
  içinde yazılır ama WebSocket push'u **transaction commit olduktan sonra** tetiklenmelidir
  (Spring'in `@TransactionalEventListener(phase = AFTER_COMMIT)` mekanizmasıyla) — aksi halde
  henüz commit olmamış, hatta rollback olabilecek bir işlem için "gerçek" bir bildirim push
  edilmiş olur.
- **Req-3.6 (Yetkilendirme — herkes kendi bildirimini görür):** `/api/bildirimler/**` ADMIN'e
  özel değil, herhangi bir kimlikli kullanıcı erişebilir; filtre rol bazlı değil, **servis içinde**
  "sadece isteyenin kendi `aliciKullaniciId`'sine ait kayıtlar" şeklinde uygulanır.
- **Req-3.7 (Hassas veri sızmasın):** `mesaj` alanına parola/hash gibi hassas veri yazılmaz —
  audit log ile aynı ilke (bkz. `requirement-audit-log.md` Req-3.3).
- **Req-3.8 (Test edilebilirlik):** WebSocket push kısmı testlerde mock/stub edilecek (gerçek
  socket bağlantısı kurmak entegrasyon testi için gereksiz karmaşıklık); DB'ye yazma kısmı gerçek
  Postgres ile (`AbstractIntegrationTest`) test edilecek — iki sorumluluk ayrı test edilebilir
  olacak şekilde (bkz. plan Faz 2 Adım 2.4).

## 4. Kapsam Dışı (Bu Aşamada Yapılmayacak)

- Bildirim tercihleri (hangi işlem türlerinin bildirim üreteceğinin kullanıcı tarafından
  kapatılabilmesi) — ilk sürümde tüm tetikleyiciler (Req-2.2) aktif.
- E-posta ile bildirim gönderme — bu, `requirement-scheduler-report.md`'deki saatlik rapor
  özelliğiyle karıştırılmıyor; ayrı ve bağımsız bir konu.
- Şema veya tag sahipliği — sadece tablo kapsamında (Req-2.1).
- Bildirim merkezinde arama/filtreleme — sadece sayfalı liste + okundu işaretleme.

## 5. Durum

Henüz implemente edilmedi. `requirement-react-custom-hooks.md`'ye **blocking bağımlı değil** ama
aynı öğrenilen custom-hook desenini (`useBildirimler`) doğal olarak takip edecek. Uygulama planı
için `plan-websocket-notifications-implementation.md`'ye bakınız.
