import { useCallback, useEffect, useState } from "react";
import { Schema, createSchema, deleteSchema, getSchemas, renameSchema } from "../api/schemas";

/**
 * Sema (schema) domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md. Sadece {@link Schema} listesini tasir (bilerek dar tutuldu):
 * sidebar'daki schema+tablo agaci ({@code tableSummariesBySchema}) buraya DAHIL DEGIL, cunku o
 * "workspace" denen ayri, 4 domain'in (sema/tablo/tag/kullanici) disinda kalan bir kavram —
 * Dashboard kendi refreshWorkspace'ini kullanmaya devam ediyor.
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4) — "kullaniciya ne gosterilecek" karari
 * hep cagiran tarafta (Dashboard'un handle* fonksiyonlarinda) kalir.
 */
export function useSchemas() {
  const [schemas, setSchemas] = useState<Schema[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setSchemas(await getSchemas());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const create = useCallback(
    async (name: string) => {
      const created = await createSchema(name);
      await refresh();
      return created;
    },
    [refresh]
  );

  const rename = useCallback(
    async (id: number, name: string) => {
      const updated = await renameSchema(id, name);
      await refresh();
      return updated;
    },
    [refresh]
  );

  const remove = useCallback(
    async (id: number) => {
      await deleteSchema(id);
      await refresh();
    },
    [refresh]
  );

  return {
    schemas,
    loading,
    refresh,
    createSchema: create,
    renameSchema: rename,
    deleteSchema: remove,
    // Kapsullemeyi BILEREK deldigi tek yer: Dashboard'daki schema silme, "hemen ekrandan kaldir,
    // 5sn icinde Geri Al'a basilmazsa asil API cagrisini yap" seklinde optimistic bir akis
    // kullaniyor (bkz. Dashboard.handleDeleteSchema) — bu, hook'un normal create/rename/delete
    // deseninden farkli, ozel bir UX. Hook'u bu tek akis icin karmasiklastirmak yerine, ham
    // setter'i acikca (ismiyle "sadece yerel/optimistic" oldugunu belli ederek) disari veriyoruz.
    setSchemasOptimistic: setSchemas,
  };
}
