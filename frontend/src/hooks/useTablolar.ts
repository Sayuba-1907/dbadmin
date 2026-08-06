import { useCallback, useState } from "react";
import {
  CreateKolonInput,
  Tablo,
  TabloUpdateRequest,
  applyTabloChanges,
  changeTabloSchema,
  createTablo,
  deleteTablo,
  getTablo,
} from "../api/tablolar";

/**
 * Tablo domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md Faz 2 Adim 2.1. useSchemalar'daki (Faz 1) pilot onaylanan
 * kalibi izler: sadece {@link Tablo} (secili tablonun tam detayi) tasir.
 * <p>
 * BILEREK DISARIDA birakilanlar:
 * <ul>
 *   <li>{@code draft} (taslak kolon state'i) — Req-3.1 geregi saf UI state, Dashboard'da kalir.
 *   <li>{@code tabloSummariesBySchema} — useSchemalar'la ayni gerekce (bkz. DECISIONS.md/sohbet):
 *       bu veri {@code getWorkspace()}'in (sema+tablo BIRLESIK) sonucundan geliyor; hook'a
 *       tasinsaydi getWorkspace() ucuncu kez (useSchemalar + burada + Dashboard'da) cagrilmis
 *       olurdu. "Workspace agaci" hicbir domain hook'una girmeyen, Dashboard'un kendi sorumlulugu
 *       olarak birakildi.
 * </ul>
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4).
 */
export function useTablolar() {
  const [selectedTablo, setSelectedTablo] = useState<Tablo | null>(null);

  const select = useCallback(async (id: number) => {
    const tablo = await getTablo(id);
    setSelectedTablo(tablo);
    return tablo;
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedTablo(null);
  }, []);

  const create = useCallback(
    async (name: string, kolonlar: CreateKolonInput[], schemaId: number) => {
      const created = await createTablo(name, kolonlar, schemaId);
      setSelectedTablo(created);
      return created;
    },
    []
  );

  /** "Kaydet'e basinca hepsi birden gitsin" akisinin tek cagrisi — bkz. TabloService.applyChanges (backend). */
  const applyChanges = useCallback(async (id: number, request: TabloUpdateRequest) => {
    const updated = await applyTabloChanges(id, request);
    setSelectedTablo(updated);
    return updated;
  }, []);

  const remove = useCallback(async (id: number) => {
    await deleteTablo(id);
  }, []);

  /**
   * Surukle-birakla dogrudan (o an acik OLMAYAN bir tabloya) uygulanan schema degisikligi.
   * Bilerek selectedTablo'yu GUNCELLEMEZ: Dashboard bu fonksiyonu sadece tasinan tablo su an
   * secili DEGILSE cagiriyor (seciliyse, API'ye hic gitmeden draft'i guncelliyor) — secili
   * olmayan baska bir tablonun sonucunu selectedTablo'ya yazmak yanlis olurdu.
   */
  const changeSchema = useCallback(async (id: number, schemaId: number) => {
    return changeTabloSchema(id, schemaId);
  }, []);

  return {
    selectedTablo,
    select,
    // Dashboard'daki "geri al"li tablo silme (handleDeleteTablo) useSchemalar'daki gibi ayri bir
    // optimistic setter'a ihtiyac duymuyor: silinen tablo secili oldugunda sadece clearSelection
    // cagrilmasi yeterli (bir LISTE degil, tek bir selectedTablo'nun null'lanmasi).
    clearSelection,
    create,
    applyChanges,
    deleteTablo: remove,
    changeSchema,
  };
}
