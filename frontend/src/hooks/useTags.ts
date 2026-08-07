import { useCallback, useState } from "react";
import { Tag, createTag, deleteTag, getTags, renameTag } from "../api/tags";

/**
 * Tag domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md Faz 2 Adim 2.2. useSchemas'daki kalibi izler, TEK farkla:
 * useSchemas mount'ta otomatik cekerken, burada BILEREK otomatik cekme YOK. Cunku mevcut
 * davranis (Req-2.3: degismeyecek) tag'lerin sadece kullanici "Tagler" sekmesine GIRINCE
 * cekilmesiydi (bkz. Dashboard.handleChangeActiveView) — mount'ta otomatik cekseydik, kullanici
 * o sekmeye hic girmese bile gereksiz bir istek atilirdi.
 * <p>
 * {@link getTagUsage} bilerek hook'a DAHIL DEGIL: "bir tag'i kullanan columns" TaglerPanel'in
 * kendi ayrinti butonuyla, kendi yerel state'inde yasayan, tags listesinden bagimsiz bir sorgu —
 * bkz. Dashboard.handleLoadTagUsage (dogrudan api/tags'ten cagirmaya devam ediyor).
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4).
 */
export function useTags() {
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setTags(await getTags());
    } finally {
      setLoading(false);
    }
  }, []);

  const create = useCallback(
    async (name: string) => {
      const created = await createTag(name);
      await refresh();
      return created;
    },
    [refresh]
  );

  const rename = useCallback(
    async (id: number, name: string) => {
      const updated = await renameTag(id, name);
      await refresh();
      return updated;
    },
    [refresh]
  );

  const remove = useCallback(async (id: number) => {
    await deleteTag(id);
  }, []);

  return {
    tags,
    loading,
    refresh,
    createTag: create,
    renameTag: rename,
    deleteTag: remove,
    // useSchemas'daki setSchemalarOptimistic ile ayni gerekce: Dashboard'daki tag silme de
    // "hemen kaldir, 5sn icinde Geri Al'a basilmazsa asil API cagrisini yap" desenini kullanir.
    setTagsOptimistic: setTags,
  };
}
