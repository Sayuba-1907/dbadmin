# GÖREV: Redis ile JWT Kimlik Doğrulama Önbelleklemesi (Caching)

`backend/notlar`, 31 Temmuz maddesi ("Yeni Teknoloji: REDIS") için requirement.

## 1. Temel Amacımız (Epic / User Story)

Sistemin veritabanı yükünü azaltmak ve yanıt süresini hızlandırmak için, JWT ile gelen isteklerdeki
kullanıcı rol/yetki doğrulama işlemini PostgreSQL yerine geçici hafızadan (Redis) yapmak.

## 2. İşlevsel Gereksinimler (Functional Requirements)

- **Req-2.1 (Okuma Kuralı):** Sisteme gelen ve JWT barındıran her istekte, kullanıcının rolü
  (yetkisi) önce Redis'ten sorgulanacaktır.
- **Req-2.2 (Yazma Kuralı - Cache Miss):** Sorgulanan veri Redis'te yoksa, sistem sessizce gidip
  veritabanından (PostgreSQL) okuyacak ve sonraki istekler için anında Redis'e kaydedecektir.
- **Req-2.3 (Login İstisnası):** Şifre doğrulaması her zaman veritabanından yapılacağı için, Login
  (giriş) işleminde Redis kesinlikle kullanılmayacak; her zaman PostgreSQL'e gidilecektir.
- **Req-2.4 (Tahliye Kuralı - Evict):** Kullanıcının rolü değiştirildiğinde veya kullanıcı sistemden
  silindiğinde, bayat veri (stale data) kalmaması için o kullanıcıya ait Redis kaydı anında (aynı
  transaction içinde) silinecektir.

## 3. Teknik Gereksinimler (Non-Functional Requirements)

- **Req-3.1 (Hata Yönetimi / Fail-Open):** Redis sunucusu çökerse, bağlantı koparsa veya yanıt
  vermezse uygulama kesinlikle hata fırlatmayacak (crash olmayacak); hatayı sadece loglayıp (error
  seviyesinde) veritabanına giderek normal akışına devam edecektir.
- **Req-3.2 (Zaman Aşımı / Timeout):** Sistemin Redis'i beklerken kilitlenmemesi için maksimum
  bağlantı bekleme süresi 200-300ms aralığında ayarlanacaktır.
- **Req-3.3 (Güvenlik Kuralı):** Parola hash'leri gibi hassas veriler Redis'e asla yazılmayacak;
  Redis sadece kimlik doğrulaması için gereken "ID ve Rol" bilgilerini tutacaktır.
- **Req-3.4 (Yaşam Süresi / TTL - Güvenlik Ağı):** JWT token süresi (örneğin 8 saat) uzun olsa da,
  Redis'teki veriler için kısa bir TTL (örneğin 30 dakika) tanımlanacaktır. Buradaki amaç JWT ile
  uyumluluk değil; olası bir evict (silme) çağrısının atlanması veya hata alması ihtimaline karşı,
  bayat verinin sistemde en fazla ne kadar süre geçerli kalabileceğini (senkronizasyon farkının üst
  sınırını) garanti eden bir güvenlik ağı oluşturmaktır.
- **Req-3.5 (Gözlemlenebilirlik) [Gelecek Adım / Henüz Yapılmadı]:** Sistemin Redis'i ne kadar
  verimli kullandığını Grafana üzerinden izleyebilmek için, uygulamanın Cache Hit ve Cache Miss
  durumları ölçülebilir olmalıdır. Bu metrik entegrasyonu mevcut kodda yoktur, bir sonraki aşamada
  eklenecektir.
- **Req-3.6 (Veri Formatı / Serialization):** Sadece iki alanlık basit bir kayıt tutulacağı için
  JSON serileştirme gibi gereksiz karmaşıklıklardan kaçınılacaktır. Veriler Redis'te düz metin
  (StringRedisSerializer) ve Hash yapısı kullanılarak saklanacaktır.
- **Req-3.7 (İzolasyon / Docker Güvenliği):** Redis veritabanı dış dünyaya tamamen kapalı olmalı,
  sadece docker network içinden erişilebilmelidir. Host makinenin dışarıya açık arayüzüne bağlayan
  `ports: "6379:6379"` kullanımı güvenlik açığı yaratacağından kullanılmayacak, yerine sadece
  container'lar arası iletişimi sağlayan `expose` tercih edilecektir.
- **Req-3.8 (Döngüsel Bağımlılık / Circular Reference):** Spring Security'nin Filter mekanizması ile
  Cache servisinin birbirini çağırıp projeyi kilitlenmesini önlemek için, Cache ayarları Security
  ayarlarından tamamen izole edilmiş ayrı bir Config dosyasında tutulacaktır.

## 4. Adım Adım İmplementasyon Planı

**Faz 1: Altyapı ve Bağımlılıkların Kurulumu**
- Adım 1.1: `pom.xml`'e Redis (`spring-boot-starter-data-redis`) bağımlılığını ekle.
- Adım 1.2: `docker-compose.yml`'e Redis servisini tanımla. Dışarıya port açmak (`ports`) yerine
  sadece backend'in erişebileceği `expose: - "6379"` kullan.
- Adım 1.3: `application.properties`'e Redis bağlantı bilgilerini ve 300ms'lik timeout ayarını ekle.

**Faz 2: Konfigürasyon Katmanı (Circular Reference Önlemi)**
- Adım 2.1: Security ayarlarından bağımsız bir `RedisConfig.java` oluştur.
- Adım 2.2: `StringRedisSerializer` ile düz metin/Hash yapısında kayıt sağlayan ayarları yap.
- Adım 2.3: Evict'in unutulması ihtimaline karşı güvenlik ağı olarak 30 dakikalık TTL'i tanımla.

**Faz 3: Cache Servisi ve Fail-Open Geliştirmesi**
- Adım 3.1: `@Cacheable` yerine özel bir `KullaniciRolCacheService.java` oluştur.
- Adım 3.2: `RedisTemplate` enjekte ederek get/put/evict metodlarını yaz.
- Adım 3.3: Her metoda try-catch ekleyerek Redis erişilemezse loglayıp null/no-op dönen fail-open
  mantığını koda dök.

**Faz 4: Security Filtresi ve İş Katmanı Entegrasyonu**
- Adım 4.1: Cache servisini `JwtAuthenticationFilter`'a enjekte et.
- Adım 4.2: Önce cache'e bak (hit), yoksa (miss) DB'den oku ve cache'e yaz.
- Adım 4.3: Rol değişimi/kullanıcı silme metodlarına, DB değişikliğiyle aynı transaction içinde
  `cacheService.evict()` çağrısını ekle.

## 5. Durum

Faz 1-4'ün büyük kısmı zaten implemente edilmiş (`9e8bf67`, 2026-07-31) — bkz. `DECISIONS.md`
"Redis: kullanici rol cache'i" bölümü. Bu doküman, süreç sırasının ("Requirement çıkart → Plan
oluştur → Implementasyon") atlanmasından sonra geriye dönük yazıldı. Yazılırken tespit edilen iki
gap:

- **Req-3.7** — `docker-compose.yml` `ports` kullanıyordu, `expose`'a çevrildi (bu doküman
  yazıldıktan sonra).
- **Req-3.5** — henüz implemente edilmedi, bilinçli olarak sonraki aşamaya bırakıldı.
