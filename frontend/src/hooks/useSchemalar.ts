import { useCallback, useEffect, useState } from "react";
import { Schema, createSchema, deleteSchema, getSchemalar, renameSchema } from "../api/schemas";

/**
 * Sema (schema) domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md. Sadece {@link Schema} listesini tasir (bilerek dar tutuldu):
 * sidebar'daki schema+tablo agaci ({@code tabloSummariesBySchema}) buraya DAHIL DEGIL, cunku o
 * "workspace" denen ayri, 4 domain'in (sema/tablo/tag/kullanici) disinda kalan bir kavram —
 * Dashboard kendi refreshWorkspace'ini kullanmaya devam ediyor.
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4) — "kullaniciya ne gosterilecek" karari
 * hep cagiran tarafta (Dashboard'un handle* fonksiyonlarinda) kalir.
 */
export function useSchemalar() {
  const [schemalar, setSchemalar] = useState<Schema[]>([]);
  const [yukleniyor, setYukleniyor] = useState(true);

  const yenile = useCallback(async () => {
    setYukleniyor(true);
    try {
      setSchemalar(await getSchemalar());
    } finally {
      setYukleniyor(false);
    }
  }, []);

  useEffect(() => {
    yenile();
  }, [yenile]);

  const create = useCallback(
    async (name: string) => {
      const created = await createSchema(name);
      await yenile();
      return created;
    },
    [yenile]
  );

  const rename = useCallback(
    async (id: number, name: string) => {
      const updated = await renameSchema(id, name);
      await yenile();
      return updated;
    },
    [yenile]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteSchema(id);
      await yenile();
    },
    [yenile]
  );

  return {
    schemalar,
    yukleniyor,
    yenile,
    createSchema: create,
    renameSchema: rename,
    deleteSchema: remove,
    // Kapsullemeyi BILEREK deldigi tek yer: Dashboard'daki schema silme, "hemen ekrandan kaldir,
    // 5sn icinde Geri Al'a basilmazsa asil API cagrisini yap" seklinde optimistic bir akis
    // kullaniyor (bkz. Dashboard.handleDeleteSchema) — bu, hook'un normal create/rename/delete
    // deseninden farkli, ozel bir UX. Hook'u bu tek akis icin karmasiklastirmak yerine, ham
    // setter'i acikca (ismiyle "sadece yerel/optimistic" oldugunu belli ederek) disari veriyoruz.
    setSchemalarOptimistic: setSchemalar,
  };
}
