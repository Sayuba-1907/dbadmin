import { useCallback, useState } from "react";
import {
  CreateColumnInput,
  Table,
  TableUpdateRequest,
  applyTableChanges,
  changeTableSchema,
  createTable,
  deleteTable,
  getTable,
} from "../api/tables";

/**
 * Table domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md Faz 2 Adim 2.1. useSchemas'daki (Faz 1) pilot onaylanan
 * kalibi izler: sadece {@link Table} (secili tablonun tam detayi) tasir.
 * <p>
 * BILEREK DISARIDA birakilanlar:
 * <ul>
 *   <li>{@code draft} (taslak kolon state'i) — Req-3.1 geregi saf UI state, Dashboard'da kalir.
 *   <li>{@code tabloSummariesBySchema} — useSchemas'la ayni gerekce (bkz. DECISIONS.md/sohbet):
 *       bu veri {@code getWorkspace()}'in (sema+table BIRLESIK) sonucundan geliyor; hook'a
 *       tasinsaydi getWorkspace() ucuncu kez (useSchemas + burada + Dashboard'da) cagrilmis
 *       olurdu. "Workspace agaci" hicbir domain hook'una girmeyen, Dashboard'un kendi sorumlulugu
 *       olarak birakildi.
 * </ul>
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4).
 */
export function useTables() {
  const [selectedTable, setSelectedTable] = useState<Table | null>(null);

  const select = useCallback(async (id: number) => {
    const table = await getTable(id);
    setSelectedTable(table);
    return table;
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedTable(null);
  }, []);

  const create = useCallback(
    async (name: string, columns: CreateColumnInput[], schemaId: number) => {
      const created = await createTable(name, columns, schemaId);
      setSelectedTable(created);
      return created;
    },
    []
  );

  /** "Kaydet'e basinca hepsi birden gitsin" akisinin tek cagrisi — bkz. TabloService.applyChanges (backend). */
  const applyChanges = useCallback(async (id: number, request: TableUpdateRequest) => {
    const updated = await applyTableChanges(id, request);
    setSelectedTable(updated);
    return updated;
  }, []);

  const remove = useCallback(async (id: number) => {
    await deleteTable(id);
  }, []);

  /**
   * Surukle-birakla dogrudan (o an acik OLMAYAN bir tabloya) uygulanan schema degisikligi.
   * Bilerek selectedTable'yu GUNCELLEMEZ: Dashboard bu fonksiyonu sadece tasinan table su an
   * secili DEGILSE cagiriyor (seciliyse, API'ye hic gitmeden draft'i guncelliyor) — secili
   * olmayan baska bir tablonun sonucunu selectedTable'ya yazmak yanlis olurdu.
   */
  const changeSchema = useCallback(async (id: number, schemaId: number) => {
    return changeTableSchema(id, schemaId);
  }, []);

  return {
    selectedTable,
    select,
    // Dashboard'daki "geri al"li table silme (handleDeleteTablo) useSchemas'daki gibi ayri bir
    // optimistic setter'a ihtiyac duymuyor: silinen table secili oldugunda sadece clearSelection
    // cagrilmasi yeterli (bir LISTE degil, tek bir selectedTable'nun null'lanmasi).
    clearSelection,
    create,
    applyChanges,
    deleteTable: remove,
    changeSchema,
  };
}
