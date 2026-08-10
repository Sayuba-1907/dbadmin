# GÖREV: Dashboard'ın Veri Katmanını Custom Hook'lara Ayırma (React Öğrenme Amaçlı)

Frontend'de öğretici bir React egzersizi arayışından çıkan requirement. Backend/Redis/JWT
belgelerindeki pattern izlenerek yazıldı; farkı şu — bu belgede **öğrenme hedefi** ve **genel
kullanım alanı** ayrı bir bölüm olarak öne çıkarılıyor, çünkü bu görevin asıl amacı proje işlevini
genişletmek değil, belirli bir React kavramını somut olarak deneyimlemek.

## 0. Öğrenme Hedefi ve Genel Kullanım Alanı

**Custom hook nedir, gerçekte ne değildir:** Custom hook, React'ın ayrı bir API'si değil — sadece
adı `use` ile başlayan ve içinde başka hook'ları (`useState`, `useEffect`, `useCallback`) çağıran
sıradan bir JavaScript fonksiyonudur. Yani zaten bilinen mekanizmanın (state + effect) yeniden
kullanılabilir bir pakete konmasından ibarettir — yeni bir kavram değil, bilinen ikisinin
kompozisyonu.

**Bu görevde asıl öğrenilecek soru şudur:** *"Bir domain'in (örn. şemalar) tüm veri sorumluluğu
— okuma, yazma, yüklenme durumu — nerede yaşamalı: component'in içinde mi, yoksa ondan ayrılmış
bir hook'ta mı?"* Bu, gerçek React projelerinde sürekli karşılaşılan bir tasarım sorusudur. Bu
görevde "hook read+write birlikte" (bkz. Req-2.2) yaklaşımını **elle** yazacağız — bunu elle
yazmak, React Query/SWR gibi kütüphanelerin çözdüğü problemi ilk elden hissetmeyi sağlıyor: aynı
kalıbı (fetch, loading state, hata yönetimi, mutasyon sonrası refetch) 4 domain için tekrar tekrar
yazınca, "bu tekrar neden bir kütüphaneye taşınmıyor" sorusu kendiliğinden ortaya çıkacak. Bu
görev bilerek o kütüphaneleri kullanmıyor (bkz. §4) — amaç, kütüphanenin çözdüğü problemi önce
elle görmek.

**Genel kullanım alanı (bu projenin dışında):** Custom hook deseni, birden fazla component'in aynı
stateful mantığı paylaştığı her yerde kullanılır — form yönetimi, sayfalama, debounce, WebSocket
bağlantısı (bkz. `requirement-websocket-notifications.md`, ayrı görev), ve en yaygın örneği: bu
projedeki gibi bir admin panelinde her varlık türü (kullanıcılar, siparişler, ürünler...) için
`useUsers()`, `useOrders()` gibi domain hook'ları. Gerçek dünyada React Query/SWR bu deseni
otomatikleştirir; bu görev o otomasyonun altında ne olduğunu gösteriyor.

## 1. Temel Amacımız (Epic / User Story)

`Dashboard.tsx`'teki (şu an 4 veri alanının — şema, tablo, tag, kullanıcı — fetch+state+mutasyon
mantığını tek dosyada tutan) veri katmanını, her domain için ayrı bir custom hook'a taşımak.
Dashboard component'i sadece UI ve kullanıcı etkileşimine odaklanacak; state lifting deseni
(child component'lerin kendi state'ini tutmaması) korunacak.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Pilot önce):** İlk olarak sadece `useSchemalar` yazılacak ve Dashboard'a bağlanacak.
  Diğer 3 domain'e (tablo, tag, kullanıcı) pilot onaylanmadan geçilmeyecek — yanlış bir API
  tasarımı 4 kez tekrarlanmadan önce tek yerde düzeltilebilsin.
- **Req-2.2 (Hook API şekli — okuma + yazma birlikte):** Her hook kendi domain'inin hem okuma
  (`data`, `yukleniyor`, `yenile`) hem yazma (`create`, `rename`/`update`, `delete`) sorumluluğunu
  taşıyacak — örn. `const { schemalar, yukleniyor, createSchema, deleteSchema } = useSchemalar()`.
  Dashboard bu fonksiyonları doğrudan çağıracak, kendi state'ini tutmayacak.
- **Req-2.3 (Mutasyon sonrası refetch korunur):** Dashboard.tsx'in mevcut ilkesi ("optimistic
  update yapmıyoruz, her mutasyondan sonra backend'den taze veri çekiyoruz") hook'ların içinde de
  aynen korunacak — davranış değişmeyecek, sadece kodun yaşadığı yer değişecek.
- **Req-2.4 (Bildirim sorumluluğu hook'ta değil):** Hata durumunda hook, hatayı yutmayacak/kendi
  bildirim göstermeyecek — fırlatacak (throw), Dashboard'daki mevcut `handleCreateSchema` gibi
  handler'lar hâlâ `try/catch` + `notifyFromError` (mevcut `NotificationProvider` altyapısı)
  çağıracak. Böylece "kullanıcıya ne gösterilecek" kararı tek bir yerde kalır.
- **Req-2.5 (Diğer domainler pilot sonrası):** `useSchemalar` onaylandıktan sonra aynı kalıp
  `useTablolar`, `useTags`, `useKullanicilar` için tekrarlanacak.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Yerel UI state hook'a taşınmaz):** Draft kolon state'i (`nextDraftKolonId`,
  kaydedilmemiş taslak kolonlar) sunucu verisi değil, saf UI state'idir — bu görev kapsamında
  hook'a taşınmayacak, `Dashboard.tsx`'te kalacak. Ayrım bilerek net tutuluyor: "sunucudan gelen
  veri" (hook'un işi) ile "sadece ekranın kendi geçici durumu" (component'in işi) karıştırılmayacak.
- **Req-3.2 (Dosya konumu):** Yeni hook'lar `frontend/src/hooks/` altında toplanacak
  (`useSchemalar.ts`, `useTablolar.ts`, ...).
- **Req-3.3 (Tip tanımları):** Hook'lar `api/*.ts`'te zaten tanımlı tipleri (`Schema`, `Tablo`,
  `Tag`, `Kullanici`) olduğu gibi kullanacak, yeni tip tanımlamayacak.
- **Req-3.4 (Test edilebilirlik artışı hedeftir):** Her hook için `@testing-library/react`'in
  `renderHook` API'siyle ayrı bir birim testi yazılacak — bu, mevcut `Dashboard.test.tsx`'in artık
  test etmek zorunda olmadığı bir sorumluluk (domain-özel fetch/mutasyon mantığı) demek; Dashboard
  testleri sadeleşmeli, hook testleri o detayı üstlenmeli.
- **Req-3.5 (Mevcut testler kırılmayacak):** Refactor sırasında `Dashboard.test.tsx` ve ilgili
  component testleri (`KullanicilarPanel.test.tsx`, `TaglerPanel.test.tsx`) yeşil kalacak şekilde
  güncellenecek.

## 4. Kapsam Dışı (Bu Aşamada Bilerek Yapılmayacak)

- **React Query / SWR gibi bir kütüphaneye geçiş** — bilerek dışarıda bırakılıyor, çünkü bu görevin
  amacı tam olarak o kütüphanelerin çözdüğü problemi elle görmek (bkz. §0). Kütüphaneye geçiş,
  bu görev tamamlanıp "bu tekrar neden otomatikleşmiyor" sorusu hissedildikten sonra, ayrı ve
  bilinçli bir sonraki adım olabilir.
- **WebSocket entegrasyonu** — ayrı bir görev (`requirement-websocket-notifications.md`), bu
  belgeye bilerek karıştırılmadı.

## 5. Durum

İmplemente edildi: Faz 1-3 tamamlandı — `useSchemas`, `useTables`, `useTags`, `useUsers` hook'ları
(`frontend/src/hooks/`), her biri kendi `renderHook` testiyle. Draft-kolon state'i (Req-3.1) hook'lara
sızmadan `Dashboard.tsx`'te kaldı. Uygulama planı için `plan-react-custom-hooks-implementation.md`'ye
bakınız.
