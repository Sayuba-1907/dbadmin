# GÖREV: Kalıcı Audit Log (Kim, Ne Zaman, Neyi Değiştirdi)

"Daha ne yapabiliriz" tartışmasından çıkan requirement. `requirement-otel-observability.md`'den
**bilerek ayrı** tutuldu — ikisi farklı ömre ve amaca sahip (bkz. §0).

## 0. Audit Log vs. OTel/Business Log — Neden Ayrı Belge

OTel tarafı (`requirement-otel-observability.md`) "şu an sistemde ne oluyor, neden yavaş" sorusuna
cevap veriyor: geçici (Loki/Tempo'da 48 saat retention), debug amaçlı. Audit log ise "6 ay önce
kim bu tabloyu sildi" sorusuna cevap vermeli: **kalıcı**, hesap verebilirlik (accountability) amaçlı,
ve OTel altyapısı (Collector/Tempo/Loki) çökse veya kapansa bile var olmaya devam etmeli. Bu yüzden
audit log OTel'in bir parçası değil, doğrudan **veritabanında** yaşayan ayrı bir mekanizma olacak —
isteğe bağlı olarak `trace_id` kolonuyla OTel trace'ine çapraz referans verebilir ama ona bağımlı
olmayacak.

## 1. Temel Amacımız (Epic / User Story)

Sistemdeki her yazma işleminin (tablo/kolon/schema/tag/kullanıcı oluşturma-değiştirme-silme) kim
tarafından, ne zaman, hangi hedefe yapıldığının kalıcı ve sorgulanabilir bir kaydını tutmak.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Kapsam):** Şu mutasyonlar audit'lenecek: tablo create/update/delete, kolon
  ekle/sil/rename/primary-key değiştir/tag ata, schema create/update/delete, tag
  create/rename/delete, kullanıcı create/rol değiştir/sil. Salt-okunur (`GET`) uçlar
  audit'lenmeyecek.
- **Req-2.2 (Kayıt içeriği):** Her audit satırı şunları tutacak: işlemi yapan kullanıcı (id +
  kullanıcı adı, silinse bile geriye dönük okunabilsin diye kullanıcı adı da ayrıca), işlem tipi
  (`TABLO_OLUSTURULDU`, `KOLON_SILINDI` gibi bir enum), hedef entity tipi + id, zaman damgası,
  opsiyonel serbest metin/JSON detay (örn. eski/yeni değer), opsiyonel `trace_id`.
- **Req-2.3 (Aynı transaction):** Audit satırı, ilgili metadata/DDL yazma işlemiyle **aynı**
  `@Transactional` metod içinde yazılacak — CLAUDE.md'nin "her yazma işlemi iki kez olur" ilkesine
  üçüncü bir yazma olarak eklenir, ayrık/asenkron yazılmaz.
- **Req-2.4 (Okuma ucu):** `GET /api/audit-loglar` eklenecek — filtre parametreleri: kullanıcı,
  hedef tip, hedef id, tarih aralığı. Sadece ADMIN erişebilecek (`/api/kullanicilar/**` ile aynı
  yetki seviyesinde, `SecurityConfig`'e eklenecek kural).
- **Req-2.5 (Değiştirilemezlik):** Audit satırları için `UPDATE`/`DELETE` uç noktası
  **yazılmayacak** — sadece `INSERT`. Yanlış bir kayıt düzeltilmek istenirse yeni bir düzeltme
  kaydı eklenir, eskisi silinmez (muhasebe defteri mantığı).

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Fail-closed, Redis'in tersi):** Redis cache "fail-open" idi (Redis çökerse sessizce DB'ye
  düş). Audit log'da bunun **tam tersi** geçerli: audit satırı yazılamazsa (örn. constraint hatası
  değil de gerçek bir DB sorunu), aynı transaction'ın parçası olduğu için işlemin tamamı rollback
  olmalı — kalıcı bir işlem audit'siz kalmamalı. Ekstra kod gerekmez, Req-2.3 (aynı transaction)
  bunu doğal olarak sağlar; ayrıca try-catch ile yutulmayacağı burada açıkça belirtiliyor.
- **Req-3.2 (`public` şema kuralına uyum):** Yeni `audit_log` tablosu da uygulamanın kendi
  metadata tablosu (`tablo`/`kolon`/`sema`/`tag`/`kullanici` gibi `public` şemada) olacak;
  `SchemaService.isHidden` mantığına ek bir istisna gerekmez çünkü zaten `public` gizli.
- **Req-3.3 (Hassas veri sızmasın):** Detay alanına parola hash'i veya JWT gibi hassas değerler
  asla yazılmayacak — sadece isim/id gibi tanımlayıcı bilgi.
- **Req-3.4 (Performans):** Her mutasyona bir ek `INSERT` — kabul edilebilir overhead (zaten aynı
  transaction'da 2 yazma varken 3. yazma marjinal maliyet).
- **Req-3.5 (Büyüme / kapsam dışı):** Tablo süresiz büyüyecek; retention/arşivleme/pagination'ın
  ölçek gerektiren bir optimizasyon olduğu ve bu aşamada gerekmediği kabul edilir — okuma ucu
  (Req-2.4) en azından sayfalama (`Pageable`) ile gelecek ki büyüdükçe `GET` ucu patlamasın.
- **Req-3.6 (OTel ile gevşek bağ):** `trace_id` kolonu doldurulacaksa (OTel implemente edildiyse),
  `Tracer` mevcut değilse `null` bırakılacak — audit log'un OTel'e bağımlılığı olmayacak, OTel
  önce ya da hiç kurulmasa bile audit log tek başına çalışabilecek.

## 4. Kapsam Dışı (Bu Aşamada Yapılmayacak)

- Audit kayıtlarını gösteren bir frontend paneli — backend ucu (Req-2.4) yeterli, UI ayrı bir iş
  olarak sonraya bırakılabilir.
- Eski/yeni değer diff'inin otomatik (reflection ile) üretilmesi — ilk sürümde her servis metodu
  kendi detay metnini elle yazacak (AOP/otomatik diff, "mekanizma görünür olsun" tercihiyle
  çelişir, bkz. plan §Faz 2).

## 5. Durum

Henüz implemente edilmedi. Uygulama planı için `plan-audit-log-implementation.md`'ye bakınız.
