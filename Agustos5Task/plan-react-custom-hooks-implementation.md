# PLAN: Dashboard'ın Veri Katmanını Custom Hook'lara Ayırma İmplementasyonu

`requirement-react-custom-hooks.md`'nin uygulama planı. Pilot-önce yaklaşımı izlenir: tek domain
ile başlanır, onaylanmadan diğerlerine geçilmez.

## Faz 1: Pilot — `useSchemalar`

- Adım 1.1: `frontend/src/hooks/useSchemalar.ts` oluştur:
  ```ts
  function useSchemalar() {
    const [schemalar, setSchemalar] = useState<Schema[]>([]);
    const [yukleniyor, setYukleniyor] = useState(true);

    const yenile = useCallback(async () => {
      setYukleniyor(true);
      setSchemalar(await getSchemalar());
      setYukleniyor(false);
    }, []);

    useEffect(() => { yenile(); }, [yenile]);

    const createSchemaVeYenile = useCallback(async (input: ...) => {
      await createSchema(input);   // hata varsa fırlatır, burada yutulmaz (Req-2.4)
      await yenile();
    }, [yenile]);

    // renameSchemaVeYenile, deleteSchemaVeYenile de ayni kalipla

    return { schemalar, yukleniyor, yenile, createSchema: createSchemaVeYenile, ... };
  }
  ```
  `api/schemas.ts`'teki mevcut fonksiyonlar (`getSchemalar`, `createSchema`, `deleteSchema`,
  `renameSchema`) olduğu gibi kullanılır, yeniden yazılmaz.
- Adım 1.2: `Dashboard.tsx`'teki `schemalar` state'ini, ilgili `useEffect`'i ve
  `handleCreateSchema`/`handleDeleteSchema` içindeki doğrudan API çağrılarını kaldır; yerine
  `const { schemalar, yukleniyor: schemalarYukleniyor, createSchema, deleteSchema } = useSchemalar()`
  koy. Handler'lar artık hook'un fonksiyonlarını çağırıp `try/catch` + `notifyFromError` ile
  sarmalayacak (Req-2.4).
- Adım 1.3: `Dashboard.test.tsx`'i çalıştır, `useSchemalar` kaynaklı kırılan testleri güncelle
  (muhtemelen mock'lanan `api/schemas.ts` çağrıları değişmeyecek, sadece render akışı).
- Adım 1.4: `frontend/src/hooks/useSchemalar.test.ts` — `renderHook` ile: ilk render'da fetch
  edildiğini, `createSchema` çağrısından sonra `yenile`'nin tetiklendiğini, hata durumunda
  promise'in reject olduğunu (Dashboard'un yakalayabilmesi için) doğrula.
- **Adım 1.5 (durma noktası):** Pilot'u gözden geçir — hook API'si (`create`/`delete` isimleri,
  dönüş şekli) rahat hissettiriyor mu? Draft-kolon benzeri bir karmaşıklık `useSchemalar`'da yok,
  bu yüzden asıl zorluk `useTablolar`'da çıkacak (Faz 2 Adım 2.1) — pilot onaylanmadan oraya
  geçilmeyecek.

## Faz 2: Kalan Domainler (Pilot Onayından Sonra)

- Adım 2.1: `useTablolar` — en karmaşığı, çünkü Dashboard'da hem tablo özetleri (schema id'sine
  göre) hem seçili tablonun tam detayı (`selectTablo`) hem de draft kolon state'i var. Bu hook
  **sadece** sunucu verisini (özetler + seçili tablo detayı) taşıyacak; draft kolon state'i
  Req-3.1 gereği Dashboard'da UI state olarak kalacak — hook'un sınırını net tutmak burada en
  kritik adım.
- Adım 2.2: `useTags` — `useSchemalar` ile aynı kalıp (basit CRUD + `getTagUsage` için ek bir
  `usage` alanı).
- Adım 2.3: `useKullanicilar` — aynı kalıp, `changeKullaniciRol` mutasyonu ekli.
- Her adımda Faz 1 Adım 1.3-1.4'teki gibi: Dashboard'ı güncelle, ilgili panel testini kontrol et,
  hook için ayrı test yaz.

## Faz 3: Dashboard Temizliği ve Son Kontrol

- Adım 3.1: `Dashboard.tsx`'in son halinde artık sadece UI state'i (seçili görünüm, draft kolon,
  form açık/kapalı gibi) ve hook çağrıları kalmalı — satır sayısındaki azalmayı gözden geçir.
- Adım 3.2: `CI=true npm test` ve `npm run test:e2e` ile tüm testlerin yeşil olduğunu doğrula.

## Sırası Önemli Notlar

- Faz 1 tamamen bitip Adım 1.5'te onay verilmeden Faz 2'ye geçilmeyecek — bu görevin amacı
  "doğru API'yi bulmak", 4 domain'i aynı anda yanlış bir kalıpla yazıp sonra hepsini düzeltmek
  öğrenme değeri açısından daha zayıf bir yol olurdu.
- Faz 2 Adım 2.1 (`useTablolar`) en riskli adım — draft kolon state'inin hook'a sızmaması özellikle
  gözden geçirilecek.

## Tahmini Kapsam

Sadece frontend, backend'e hiç dokunmuyor. Redis entegrasyonundan küçük — 4 yeni dosya (+4 test
dosyası), `Dashboard.tsx`'te net bir küçülme.
