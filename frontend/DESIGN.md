# DBAdmin — Tasarım Sistemi

Bu doküman `frontend güzelleştirme` (notlar, 28 Temmuz / 3 Ağustos) maddesinin tasarım planıdır.
Koda geçmeden önce onaylanması için yazıldı — onaylanınca "Mimari" bölümündeki fazlara göre
uygulanacak.

## 1) Konu ve kimlik

DBAdmin gerçek bir PostgreSQL'i yöneten iç araç — hedef kitle geliştirici/admin, iş bir
pazarlama sitesi değil, yoğun veri yönetimi (bkz. `DECISIONS.md` → Frontend/Layout: phpMyAdmin
/ Supabase / Prisma Studio kategorisi). O yüzden tasarım yönü "cesur/dekoratif" değil,
**teknik, sakin, yoğunluk kaldırabilen** bir kimlik. Karanlık tema varsayılan — bu kategorideki
tüm referanslar (Supabase, Prisma Studio, DBeaver dark, pgAdmin dark) zaten öyle — ama tek tema
ile sınırlı kalınmadı: `ThemeProvider`/`ThemeSwitcher` ile bir light/dark toggle desteklenir,
`tokens.css`'teki `[data-theme="light"]` bloğu açık temanın karşılık gelen token değerlerini
tanımlar.

**İmza fikri — "kod kimliği" ayrımı:** Uygulamada iki tür isim var: gerçek bir Postgres
nesnesinin adı (schema, tablo, kolon, tag — hepsi `NameValidator`'dan geçen, DB'de karşılığı
olan teknik kimlikler) ve bir insan kaydının adı (kullanıcı adı, rol). Birincisi her yerde
**monospace + accent rengi** ile, ikincisi **sans-serif** ile gösterilecek. Tek bakışta "bu
bir DB nesnesi mi, insan kaydı mı" ayrımı yapılabiliyor olacak — çoğu admin panelinde bu
ayrım bilinçli yapılmıyor, DBAdmin'in kendine has detayı bu olacak. (Bu kavramın kendisi ve
tüm yapısal kararlar — kart, boş durum, rozetler — bize ait; §8'de değişen sadece ham
renk/font değerleri, kimlik mantığı aynı kaldı.)

İkincil imza: **amber = ayrıcalıklı/özel** anlamı tüm uygulamada tutarlı — PK badge zaten
amber, admin rol rozeti de aynı aileden olacak (bkz. §4).

## 2) Renk sistemi

Üç katmanlı yüzey hiyerarşisi: `bg` (sayfa zemini) → `surface` (header/sidebar/nav gibi
sabit paneller) → `surface-raised` (modal, dropdown, hover). Koyu temada gölge yerine bu
katman farkı elevation'ı taşıyor (gölgeler koyu zeminde neredeyse görünmez).

> **Güncel değerler (§8, ui-ux-pro-max "Developer Tool/IDE" paletinden, 2026-08-03):**
> aşağıdaki tablo bu paleti yansıtıyor. İlk tasarımdaki değerler (teal `#2DD4BF` bazlı)
> §8'de referans olarak duruyor.

| Token | Hex | Kullanım |
|---|---|---|
| `--color-bg` | `#0F172A` | Sayfa zemini, detay panelin arka planı |
| `--color-surface` | `#1B2336` | Header, sidebar, workspace-nav, tablo/kart zemini |
| `--color-surface-raised` | `#334155` | Modal, dropdown, hover durumları |
| `--color-border` | `#475569` | İnce ayraç çizgileri |
| `--color-border-strong` | `#64748B` | Hover/focus'ta belirginleşen kenarlık |
| `--color-text-primary` | `#F8FAFC` | Ana metin |
| `--color-text-secondary` | `#94A3B8` | İkincil metin, sayaç, hint, placeholder |
| `--color-text-disabled` | `#64748B` | VIEWER rolünde kapalı alanlar |
| `--color-accent` | `#22C55E` | Link/seçili metin, focus ring, imza rengi |
| `--color-accent-strong` | `#16A34A` | Primary buton zemini, aktif nav/dil butonu zemini |
| `--color-accent-soft` | `rgba(34,197,94,.14)` | Seçili satır zemini, drag-over zemini |
| `--color-on-accent` | `#0F172A` | Accent zemin üstündeki metin |
| `--color-success` | `#16A34A` | Başarı toast'ı (artık accent-strong ile aynı yeşil aile — bkz. §8) |
| `--color-conflict` | `#D97706` | 409 toast'ı (mevcutla aynı) |
| `--color-danger` | `#EF4444` | Sil butonu/linki, hata metni |
| `--color-danger-bg` | `rgba(239,68,68,.14)` | Hata toast'ı zemini gerekirse |

**Tip rozeti (type-badge) renk ailesi — 4 farklı hue, birbirine karışmasın diye:**

| Tip | Metin rengi | Zemin |
|---|---|---|
| `numeric` | `#60A5FA` (mavi) | `rgba(96,165,250,.16)` |
| `text` | `#4ADE80` (yeşil) | `rgba(74,222,128,.16)` |
| `datetime` | `#C084FC` (mor) | `rgba(192,132,252,.16)` |
| `boolean` | `#818CF8` (indigo) | `rgba(129,140,248,.16)` |

**PK badge:** mevcut amber ailesi korunuyor, koyu temaya uyarlanıyor → metin `#FBBF24`,
zemin `rgba(245,158,11,.18)`.

**Rol rozeti (yeni, §4):** `VIEWER` → `--color-text-secondary` zemin yok (nötr), `EDITOR` →
mavi aile (`numeric` ile aynı ton), `ADMIN` → amber aile (PK ile aynı ton — "ayrıcalıklı"
anlamını taşıyor).

Notification renkleri (`notifications.css`) zaten iyi çalışıyor (yeşil/amber/kırmızı, beyaz
metin) — değiştirilmiyor, sadece yeni gölge token'ına hizalanıyor.

## 3) Tipografi

- `--font-sans`: `'Fira Sans', -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif` —
  UI metni (nav, buton, etiket, başlık, kullanıcı adı, rol). (İlk tasarım: Inter, §8'de
  ui-ux-pro-max önerisiyle Fira Sans'a değişti.)
- `--font-mono`: `'Fira Code', ui-monospace, Menlo, Consolas, monospace` — DB kimlikleri:
  tablo/kolon/schema/tag adı, type-badge, pk-badge, `tagler-usage-list` (zaten monospace,
  korunuyor). (İlk tasarım: JetBrains Mono.)

Google Fonts: Fira Sans (400/500/600/700) + Fira Code (400/500/600), `public/index.html`'e
`<link>` ile eklendi.

| Token | px | Kullanım |
|---|---|---|
| `--text-xs` | 11 | Badge, sayaç, `you-badge` |
| `--text-sm` | 12–13 | Hint, ikincil etiket |
| `--text-base` | 14 | Gövde/tablo (mevcut varsayılan, çoğu yer zaten bu) |
| `--text-lg` | 18 | Detay panel tablo adı |
| `--text-xl` | 20 | `app-header` marka adı "DBAdmin" |

Ağırlık: 400 gövde, 500 vurgulu (seçili öğe, tablo adı), 600 başlık, 700 badge metni.

## 4) Bileşen kararları

- **Header:** zemin `surface`, alt çizgi `border`. "DBAdmin" yazısının önüne küçük bir
  `brand-mark` (accent renkli, radius'lu 8×8 kare) eklenecek — login ekranında da aynı işaret
  kullanılacak, marka önce login'de görülüyor olacak.
- **Login formu:** zemin `surface-raised`, `shadow-md`, input focus ring accent.
- **Workspace nav / dil switcher (aktif durum):** zemin `accent-strong`, metin `on-accent`
  (şu an dolgun mavi `#2563eb` + beyaz metin — aynı mantık, yeni palete taşınıyor).
- **Sidebar / schema ağacı:** tablo adları `mono` + `text-primary`; seçili tablo satırı artık
  sadece zemin değil, **sol kenarda 2px accent çubuk** da alıyor (yeni, imza detayı) + zemin
  `accent-soft`, metin `accent`. Drag-over durumu aynı mantıkla `accent-soft` zemin.
- **Detay panel / kolon tablosu:** satır hover'ı yeni eklenıyor (şu an yok) → zemin `surface`.
  Kolon adı hücresi `mono`. Type-badge §2'deki 4 renkten class'ına göre (`type-badge-numeric`
  vb.) boyanıyor. Tablo adı input'u `mono` + `text-lg`.
- **Tagler paneli:** tag adı `mono` (bunlar da `NameValidator`'dan geçen gerçek kimlikler).
- **Kullanıcılar paneli:** kullanıcı adı **sans** kalıyor (insan kaydı, DB nesnesi değil), rol
  hücresine yeni `role-badge` rozeti (§2 renk tablosu) sarılıyor.
- **Modal:** zemin `surface-raised`, overlay `rgba(4,6,10,1)` (tam opak — arkadaki içerik,
  özellikle boş durumun parlak teal yanıp sönen imleci, `.6` → `.88` → `.97` kademelerinde
  bile hâlâ hafifçe seziliyordu; teal doygun bir renk olduğu için %97'de bile fark
  ediliyordu, tek kesin çözüm tam opaklık oldu), `shadow-md`, radius 8 (mevcut).
- **Butonlar:** `.btn-primary` → zemin `accent-strong`/metin `on-accent`, hover `accent`.
  `.btn` (ikincil) → zemin `surface-raised`, kenarlık `border`, hover kenarlık
  `border-strong`. `.btn-danger` → metin `danger`. Focus ring hepsinde `accent` (eski
  `#2563eb` yerine).

## 5) Spacing / radius / motion

4px taban: `--space-1..8` = 4/8/12/16/20/24/32px (mevcut boşluklar zaten çoğunlukla bu
grid'e uyuyor, sadece token'a bağlanıyor). Radius: `--radius-sm 4px` (input/küçük buton),
`--radius-md 6px` (badge/sidebar öğeleri), `--radius-lg 8px` (modal/login kart). Geçişler
mevcut `0.15s ease` civarında korunuyor, `prefers-reduced-motion: reduce` için global bir
media query eklenip tüm `transition`lar sıfırlanacak (erişilebilirlik).

## 6) Mimari — kodda nasıl uygulanacak

Var olan yapı korunuyor (App.css tek dosya, sınıf adları aynı) — **sıfırdan yazım değil,
token'a bağlama refactor'ü**. Sadece bir yeni dosya ve birkaç yeni class ekleniyor:

- **Yeni:** `frontend/src/styles/tokens.css` — yukarıdaki tüm `--color-*`/`--font-*`/
  `--text-*`/`--space-*`/`--radius-*` değişkenleri `:root` içinde.
- **`index.css`:** en üste `@import './styles/tokens.css';`, `body` → `var(--font-sans)`,
  `code` → `var(--font-mono)`.
- **`App.css`:** değişmiyor yapısal olarak — içindeki hardcoded hex/px değerleri
  `var(--token)` çağrılarıyla değiştiriliyor. Yeni class'lar: `.type-badge-numeric/-text/
  -datetime/-boolean`, `.role-badge` + `.role-badge-admin/-editor/-viewer`, `.brand-mark`,
  `.mono` (identifier'lara eklenecek utility).
- **`notifications.css`:** sadece gölge token'ı, renkler aynı kalıyor.
- **`public/index.html`:** Google Fonts `<link>` + `preconnect`.
- **TSX tarafında değişen tek şey:** ilgili yerlere `className="mono"` / `role-badge-*`
  eklemek (`TabloSidebar`, `TabloDetail`, `KolonRow`, `TaglerPanel`, `KullanicilarPanel`) —
  state/mantık değişmiyor, sadece render'a class ekleniyor.

**Uygulama fazları** (her faz bağımsız test edilebilir, birini onaylamadan sonrakine
geçmiyorum):

1. `tokens.css` + `index.css` + font linkleri — altyapı, henüz görsel fark yok.
2. Genel: header, login, dil switcher, `.btn*`, focus ring'ler.
3. Sidebar: workspace-nav, schema ağacı, tablo listesi + `mono` class'ı.
4. Detay panel: kolon tablosu, type/PK badge'leri, tablo adı input'u.
5. Tagler + Kullanıcılar panelleri + modal + rol rozeti.
6. `notifications.css` hizalama + `prefers-reduced-motion` + son genel geçiş/ekran görüntüsü.

Her fazın sonunda ekran görüntüsü alıp burada göstereceğim, sıradaki faza onay alınca
geçeceğim.

## 7) İkinci geçiş — "sadece renk değiştirmiş" hissini kırmak

6 faz bittikten sonra yapı hâlâ birebir eskisiyle aynıydı, sadece palet değişmişti — bu,
`frontend-design` skill'inin tam uyardığı "templated reskin" hatası. 5 somut ekleme yapıldı:

- **Boş durum artık bir terminal anı** (`Dashboard.tsx`): "Select a table" yerine
  `-- Select a table_` — monospace, yanıp sönen imleç (`.empty-state-cursor`,
  `prefers-reduced-motion`'da otomatik duruyor çünkü animasyon global media query'de
  sıfırlanıyor). "Kod kimliği" imzasının kullanıcının ilk gördüğü an olması hedeflendi.
- **Detay paneli artık bir kart içinde** (`.detail-card`, `TabloDetail.tsx`): kolon tablosu +
  iki form artık `surface` zemin + `border` + `radius-lg` ile çerçeveli, düz zeminde
  yüzmüyor. Kolon satırı hover'ı `surface-raised`'e çekildi (kart zaten `surface` olduğu
  için eski hover rengiyle çakışırdı).
- **Schema'lara isimden türetilmiş renk noktası** (`schemaColor()`, `TabloSidebar.tsx`):
  basit bir string hash → hue (0-360), aynı isim her zaman aynı rengi üretir. Çok schema
  olunca göz taraması kolaylaşır, backend'de karşılığı yok, salt istemci tarafı.
  Sınırı: sadece string hash olduğu için ender de olsa iki farklı schema aynı hue'ya
  düşebilir, kritik değil çünkü sadece görsel bir ipucu.
- **Workspace nav'a küçük ikonlar** (`▦` Schemas, `◈` Tags, `◉` Users) — geometrik unicode
  glif seti, dış ikon kütüphanesi eklenmedi.
- **Mikro-etkileşimler**: `.btn:active`/`.workspace-nav-btn:active` hafif `scale(0.97)`,
  `.tablo-list-item:hover` `translateX(2px)` — tıklama/hover'da tıklanabilirlik hissi.

## 8) Palet değişikliği — ui-ux-pro-max karşılaştırması (2026-08-03)

`backend/week3-uiux-deneme` branch'inde `ui-ux-pro-max-skill` denendi (bkz. sohbet geçmişi).
Skill'in kendi CSV veritabanından "Developer Tool / IDE" + "Dark Mode (OLED)" araması, slate +
yeşil bir palet ve Fira Sans/Fira Code font ikilisi önerdi. Karşılaştırıldı, bu palet tercih
edildi ve kalıcı hale getirildi:

- **Ne değişti:** sadece `tokens.css`'teki ham renk/font değerleri (yukarıdaki §2/§3
  tabloları güncel değerleri gösteriyor). Yapısal/imza kararların (mono/sans ayrımı, terminal
  boş durumu, `detail-card`, schema renk noktaları, rozetler, mikro-etkileşimler) hiçbiri
  değişmedi — onlar bu projeye özel, skill'in üretmediği kararlar.
- **Neden ui-ux-pro-max'ın önerisi işe yaradı:** eski teal (`#2DD4BF`) accent, zaten var olan
  yeşil `success`/`type-badge-text` renkleriyle hafif çakışıyordu (iki farklı "yeşilimsi" ton
  yarışıyordu). Yeni palette accent de yeşil (`#22C55E`) olduğu için tüm bu renkler tek bir
  aileye toplandı — tutarlılık, skill'in "akıllı" bir önerisinden çok var olan bir çakışmanın
  çözülmesinden geliyor.
- **Kart gölgesi ve seçili satır vurgusu güçlendirildi** (2026-08-03, kullanıcı geri
  bildirimiyle — ilk palet karşılaştırmasında arayüz "çok sade" hissettirdi): `--shadow-md`
  `0 4px 16px rgba(0,0,0,.5)` → `0 8px 24px rgba(0,0,0,.65)`; `.tablo-list-item.selected`
  sol çubuğu 2px → 3px + `box-shadow: inset` ile hafif glow + `font-weight: 600`.
  Not: GitHub'daki showcase görselleri (landing page/hero/gradient ağırlıklı) ile
  karıştırılmamalı — o örnekler pazarlama sayfaları için, DBAdmin bir veri tablosu aracı;
  oraya özgü gösterişi (gradyan, illüstrasyon, hero section) buraya taşımak yanlış yön olurdu.
