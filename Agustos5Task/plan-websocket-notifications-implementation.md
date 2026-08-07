# PLAN: Tablo Sahipliği + Canlı Bildirim Sistemi İmplementasyonu

`requirement-websocket-notifications.md`'nin uygulama planı.

## Faz 1: Backend — Tablo Sahipliği

- Adım 1.1: `Tablo` entity'sine `olusturanKullaniciId` (nullable `Long`) ekle — `schema`/
  `updatedAt` alanlarındaki mevcut desenle aynı gerekçe (Hibernate `ddl-auto=update` var olan
  satırları doldurmadan `NOT NULL` kolon açamıyor).
- Adım 1.2: `TabloService.createTablo`'da aktif kullanıcıyı `SecurityContextHolder`'dan okuyup
  alanı doldur.
- Adım 1.3: Backfill — uygulama başlangıcında (mevcut `KullaniciSeeder` örneğine benzer bir
  `@Component`/`ApplicationRunner`) `olusturanKullaniciId IS NULL` olan tabloları bulup ilk ADMIN
  kullanıcının id'sine ata (Req-3.2). Sadece bir kez, tablo boşsa hiçbir şey yapmaz (idempotent —
  her başlangıçta çalışsa da zaten dolu satırları tekrar değiştirmez).

## Faz 2: Backend — Bildirim Entity ve Servis

- Adım 2.1: `entity/Bildirim.java` — `id`, `aliciKullaniciId`, `tabloId`, `tabloAdi`,
  `tetikleyenKullaniciAdi`, `tur` (enum: `KOLON_EKLENDI`, `KOLON_SILINDI`, `TABLO_YENIDEN_ADLANDIRILDI`,
  `TABLO_SILINDI`, vb.), `mesaj`, `okunduMu` (boolean, default false), `olusturulmaZamani`.
- Adım 2.2: `repository/BildirimRepository.java` — `findByAliciKullaniciIdOrderByOlusturulmaZamaniDesc`
  (`Pageable`), `countByAliciKullaniciIdAndOkunduMuFalse`.
- Adım 2.3: `service/BildirimService.java` — `olustur(Tablo tablo, Kullanici tetikleyen, Tur tur)`:
  `tetikleyen.getId().equals(tablo.getOlusturanKullaniciId())` ise no-op (Req-3.3); değilse
  `Bildirim` satırını kaydet ve bir `BildirimOlusturulduEvent` publish et (`ApplicationEventPublisher`).
- Adım 2.4: `TabloService`'teki mutasyon metodlarına (`renameTablo`, `deleteTablo`, `addKolon`,
  `deleteKolon`, `renameKolon`, `changeKolonTag`, `changePrimaryKey`, `changeTabloSchema`) elle
  `bildirimService.olustur(...)` çağrısı ekle — audit log'daki gibi **explicit**, AOP değil
  (koşullu iş kuralı — "owner mı değil mi" — generic bir aspect'e uygun değil).
- Adım 2.5: `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` ile
  `BildirimOlusturulduEvent`'i dinleyen bir listener (`BildirimPushListener`) — commit'ten sonra
  tetiklenir, Faz 3'teki WebSocket registry'yi kullanarak push eder (Req-3.5, en kritik teknik
  detay: push asla commit'ten önce olmayacak).

## Faz 3: Backend — WebSocket Altyapısı

- Adım 3.1: `websocket/WebSocketConfig.java` — ham `TextWebSocketHandler` kaydı (`/ws` endpoint),
  STOMP/SockJS **kullanılmıyor** — mekanizma görünür kalsın kararı (JWT/Redis'i elle yazma
  tercihiyle aynı çizgi).
- Adım 3.2: `websocket/BildirimWebSocketHandler.java` — `afterConnectionEstablished`'da handshake
  query param'ından (`?token=...`) JWT'yi al, `JwtService` ile doğrula, `kullaniciId`'yi session
  attribute'una koy; doğrulama başarısızsa bağlantıyı kapat.
- Adım 3.3: `websocket/WebSocketSessionRegistry.java` — `ConcurrentHashMap<Long, Set<WebSocketSession>>`,
  `ekle`/`cikar`/`gonder(kullaniciId, mesaj)` metotları. `gonder` best-effort: session yoksa/kapalıysa
  sessizce no-op (Req-3.4).
- Adım 3.4: `afterConnectionClosed`'da registry'den session'ı temizle (bağlantı kopunca "hayalet"
  session kalmasın).

## Faz 4: Backend — Okuma / Okundu-İşaretleme Uçları

- Adım 4.1: `controller/BildirimController.java`:
  - `GET /api/bildirimler` (`Pageable`)
  - `GET /api/bildirimler/okunmamis-sayisi`
  - `PATCH /api/bildirimler/{id}/okundu`
  - `PATCH /api/bildirimler/okundu` (tümünü okundu işaretle)
- Adım 4.2: `SecurityConfig`'e `/api/bildirimler/**` için `authenticated()` kuralı ekle (rol
  kısıtı yok — Req-3.6); `BildirimController`/`BildirimService` içinde her sorgu aktif kullanıcının
  id'siyle filtrelenir, başka bir kullanıcının bildirimine asla erişilemez.

## Faz 5: Frontend

- Adım 5.1: `api/bildirimler.ts` — REST çağrıları (mevcut `api/*.ts` dosyalarıyla aynı kalıp).
- Adım 5.2: `hooks/useWebSocket.ts` — genel amaçlı bağlantı hook'u: `ws://.../ws?token=...`'a
  bağlanır, `onclose`'da basit backoff ile yeniden dener, `useEffect` cleanup'ta `socket.close()`
  çağırır (daha önce konuşulan "AbortController/cleanup" dersiyle aynı aile).
- Adım 5.3: `hooks/useBildirimler.ts` — mount'ta bir kez `okunmamis-sayisi`'nı çeker (Req-2.5),
  `useWebSocket`'ten gelen bildirim event'leriyle sayaç+listeyi **yerelde** günceller (Req-2.4),
  `okundu` fonksiyonlarını dışa açar. Az önceki custom-hook refactor'ünde öğrenilen "okuma+yazma
  birlikte" kalıbının doğal bir devamı.
- Adım 5.4: `WorkspaceNav.tsx`'e zil ikonu + `useBildirimler().okunmamisSayisi` ile dolan bir badge.
- Adım 5.5: Bildirim merkezi paneli (yeni component, örn. `BildirimPanel.tsx`) — zile tıklanınca
  açılan bir dropdown/panel; listeyi gösterir, bir bildirime tıklanınca `okundu` işaretlenir ve
  ilgili tabloya navigasyon yapılır.
- Adım 5.6: Popup — `useBildirimler` yeni bir event aldığında mevcut `NotificationProvider.notify(...)`'ı
  çağırır (Req-2.8, yeni bir toast mekanizması yazılmaz).

## Faz 6: Test

- Adım 6.1: `BildirimServiceTest` (gerçek Postgres) — owner değilse satırın oluştuğunu, owner ise
  oluşmadığını (Req-3.3) doğrula.
- Adım 6.2: Metadata/DDL yazımı sırasında bir hata enjekte edilip transaction'ın tamamen rollback
  olduğunda `Bildirim` satırının da yazılmadığını doğrulayan test (Req-3.4'ün fail-closed yarısı).
- Adım 6.3: `AFTER_COMMIT` davranışını doğrulayan test — `WebSocketSessionRegistry.gonder(...)`'ın
  yalnızca transaction commit olduktan **sonra** çağrıldığını (mock registry ile) doğrula.
- Adım 6.4: `SecurityRulesIntegrationTest`'e, bir kullanıcının `/api/bildirimler`'da başka bir
  kullanıcının bildirimini göremediğini doğrulayan bir test ekle.
- Adım 6.5: Frontend `useBildirimler.test.ts` — mock bir WebSocket event geldiğinde sayaç ve
  listenin yerelde (yeniden fetch olmadan) güncellendiğini doğrula.

## Sırası Önemli Notlar

- Faz 1 (owner alanı), Faz 2'den önce bitmiş olmalı — `BildirimService.olustur` owner karşılaştırması
  yapıyor.
- Faz 3 (WebSocket registry), Faz 2 Adım 2.5'ten önce bitmiş olmalı — push için registry'nin var
  olması gerekiyor.
- Faz 4-5 birbirinden bağımsız, paralel ilerlenebilir.
- Faz 2 Adım 2.5 (`AFTER_COMMIT`) bu planın en kolay atlanabilecek/unutulabilecek adımı — commit
  öncesi push edilirse rollback olan bir işlem için "yalan" bildirim gitmiş olur, bu yüzden Faz 6
  Adım 6.3'teki test özellikle bunu doğrulamalı.

## Tahmini Kapsam

Şimdiye kadarki en büyük parça: yeni bir domain (`Bildirim`), yeni bir iletişim kanalı
(WebSocket + session registry), hem backend hem frontend değişikliği, ve `Tablo` entity'sine
şema değişikliği (owner alanı + backfill). OTel'den de büyük — birden fazla haftalık PR'a
bölünmesi makul (örn. Faz 1-2 bir PR, Faz 3-4 bir PR, Faz 5-6 bir PR).
