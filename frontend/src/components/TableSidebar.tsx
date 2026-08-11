import { DragEvent, FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Tree } from "primereact/tree";
import { TreeNode } from "primereact/treenode";
import { InputText } from "primereact/inputtext";
import { Schema, TableSummary } from "../api/schemas";
import { Column, Table } from "../api/tables";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../notifications/ConfirmProvider";

/**
 * Schema adindan deterministik bir renk turetir (ayni isim her zaman ayni renk) — sidebar'da
 * cok schema oldugunda goz taramasini kolaylastiran salt gorsel bir ipucu, backend'de karsiligi
 * yok. Basit bir string hash'i hue'ya (0-360) esliyor, sabit doygunluk/parlaklik koyu temada
 * hepsinin okunakli kalmasini sagliyor.
 */
function schemaColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue}, 65%, 60%)`;
}

/** Tree'nin dugum verisinde (data alani) tasidigimiz ozel bilgi — schema mi, tablo mu, bir
 * tablonun altindaki tekil kolon mu, yoksa "bos" / "yukleniyor" bir yer tutucu satir mi
 * oldugunu ayirt eder. */
type NodeData =
  | { type: "schema"; schema: Schema }
  | { type: "table"; table: TableSummary; schemaId: number }
  | { type: "column"; column: Column }
  | { type: "emptySchema" }
  | { type: "emptyTable" }
  | { type: "loadingColumns" };

/**
 * Props: gercek veri (schemas, her schema'nin tablo ozetleri, selectedId) ve "bir seye
 * tikladiginda ne olsun" callback'leri hep Dashboard'dan gelir. Bu component'in kendi tuttugu
 * state, arama kutusunun metni, hangi schema'larin acik (expanded) oldugu ve hangi schema su an
 * yeniden adlandirma modunda oldugu — hepsi salt gorsel/gecici UI durumu, Dashboard'un ya da
 * backend'in hic umrunda degil.
 * <p>
 * Her schema'nin altindaki tablo listesi TableSummary (sadece id/name/columnCount) — tablonun
 * kolonlarinin tam listesi burada YOK. Bir tabloya TIKLAMAK (secmek) onu sag paneldeki
 * TableDetail icin ceker (Dashboard.selectTablo); bir tabloyu agacta GENISLETMEK ise ayrica,
 * lazy olarak {@link onLoadKolonlar} ile ceker ve {@code kolonlarByTabloId}'de onbelleklenir —
 * ikisi ayni veriyi farkli amaclarla iki kere isteyebilir (basitlik icin bilerek boyle, TaglerPanel'in
 * kullanim onbellegiyle ayni desen).
 */
interface TableSidebarProps {
  schemas: Schema[];
  tableSummariesBySchema: Record<number, TableSummary[]>;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreateClick: () => void;
  onCreateSchemaClick: () => void;
  onRenameSchema: (id: number, name: string) => Promise<void>;
  onDeleteSchema: (id: number) => void;
  onChangeTableSchema: (id: number, schemaId: number) => Promise<void>;
  onLoadColumns: (tableId: number) => Promise<Table>;
}

/**
 * Sol tarafta schema -> tablo hiyerarsisi + "yeni tablo"/"yeni schema" butonlari + arama kutusu.
 * <p>
 * PrimeReact'in {@link Tree} bileseni SADECE acilir/kapanir iskelet + klavye/erisilebilirlik
 * mekanigi icin kullaniliyor — surukle-birak, yeniden adlandirma formu, renk noktasi gibi her
 * sey hala bizim kendi {@code nodeTemplate}'imizde, kendi state'imizle yonetiliyor (Tree'nin
 * kendi {@code dragdropScope} DnD'sine BILEREK guvenilmedi: bizim "tabloyu schema'ya tasi"
 * semantigimiz Tree'nin "dugumu yeniden sirala/tasi" modeliyle birebir ortusmuyor).
 */
export function TableSidebar({
  schemas,
  tableSummariesBySchema,
  selectedId,
  onSelect,
  onCreateClick,
  onCreateSchemaClick,
  onRenameSchema,
  onDeleteSchema,
  onChangeTableSchema,
  onLoadColumns,
}: TableSidebarProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const confirm = useConfirm();
  const [query, setQuery] = useState("");
  const [expandedSchemaIds, setExpandedSchemaIds] = useState<Set<number>>(new Set());
  const [editingSchemaId, setEditingSchemaId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");
  // Agacta bir tablo genisletilince (bkz. handleToggle) kolonlari lazy cekilir ve burada
  // onbelleklenir — ayni tabloyu tekrar ac/kapa yapmak ikinci bir istek atmaz (TaglerPanel'in
  // kullanim onbellegiyle ayni desen).
  const [expandedTableIds, setExpandedTableIds] = useState<Set<number>>(new Set());
  const [columnsByTableId, setColumnsByTableId] = useState<Map<number, Column[]>>(new Map());
  const [loadingTableIds, setLoadingTableIds] = useState<Set<number>>(new Set());
  // Surukle-birak sirasinda hangi tablo hangi schema'dan tasiniyor ve hangi schema basligi
  // hedef olarak vurgulanmali — hepsi salt gorsel/gecici state, backend'in umrunde degil.
  // draggedFromSchemaId ayrica tutuluyor cunku TableSummary'de schemaId alani yok (sadece
  // id/name/columnCount) — tablonun su an hangi schema'da oldugunu sadece hangi listeden
  // suruklendiginden biliyoruz.
  const [draggedTableId, setDraggedTableId] = useState<number | null>(null);
  const [draggedFromSchemaId, setDraggedFromSchemaId] = useState<number | null>(null);
  const [dragOverSchemaId, setDragOverSchemaId] = useState<number | null>(null);
  // Her schema'nin altindaki tablo listesinin hangi kritere gore siralanacagi — sadece
  // gorsel/gecici UI durumu, backend'e hic gitmiyor (backend zaten isme gore sirali doner,
  // "kolon sayisi" secenegi tamamen frontend'de, elimizdeki ozet veri uzerinde hesaplaniyor).
  const [sortBy, setSortBy] = useState<"name" | "columnCount">("name");

  /** Bir tablo, uzerine birakildigi schema'nin basligina surukle-birakla tasinir. Zaten o schema'daysa hicbir sey yapmaz. */
  function handleDropOnSchema(event: DragEvent, targetSchemaId: number) {
    event.preventDefault();
    setDragOverSchemaId(null);
    const tableId = draggedTableId;
    const fromSchemaId = draggedFromSchemaId;
    setDraggedTableId(null);
    setDraggedFromSchemaId(null);
    if (tableId == null) {
      return;
    }
    if (fromSchemaId !== targetSchemaId) {
      onChangeTableSchema(tableId, targetSchemaId);
    }
  }

  async function handleRenameSubmit(event: FormEvent, schemaId: number) {
    event.preventDefault();
    await onRenameSchema(schemaId, renameDraft);
    setEditingSchemaId(null);
  }

  // Buyuk/kucuk harf duyarsiz, basit bir "icinde geciyor mu" filtresi — backend'e hic gitmiyor,
  // tamamen zaten ekranda olan (yuklenmis) ozet listelerin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const isSearching = normalizedQuery !== "";

  function sortedTablesOf(schemaId: number): TableSummary[] {
    const all = tableSummariesBySchema[schemaId] ?? [];
    const filtered = isSearching
      ? all.filter((table) => table.name.toLowerCase().includes(normalizedQuery))
      : all;
    // "columnCount" -> en cok kolonlu tablo en ustte (azalan, b - a).
    // "name" -> Turkce alfabetik siraya gore (localeCompare "tr").
    return [...filtered].sort((a, b) =>
      sortBy === "columnCount" ? b.columnCount - a.columnCount : a.name.localeCompare(b.name, "tr")
    );
  }

  const hasAnyTable = Object.values(tableSummariesBySchema).some((list) => list.length > 0);

  // Arama yaparken bir schema'nin altinda hic eslesme yoksa o schema'yi tamamen gizliyoruz;
  // aramiyorsak (bos schema dahil) her schema gorunur — kullanici yeni actigi bos bir schema'yi
  // "kayboldu" sanmasin diye.
  const visibleSchemas = isSearching
    ? schemas.filter((schema) => sortedTablesOf(schema.id).length > 0)
    : schemas;

  /** Bir tablo dugumunun altindaki kolon dugumlerini kurar — henuz cekilmemisse bos (Tree
   * "children yok" sanip yaprak gibi davranmaz, cunku tablo dugumunun kendisi leaf:false). */
  function tableChildren(tableId: number): TreeNode[] {
    if (loadingTableIds.has(tableId)) {
      return [
        {
          key: `table-${tableId}-loading`,
          data: { type: "loadingColumns" } satisfies NodeData,
          leaf: true,
        },
      ];
    }
    const columns = columnsByTableId.get(tableId);
    if (!columns) {
      return [];
    }
    if (columns.length === 0) {
      return [
        {
          key: `table-${tableId}-empty`,
          data: { type: "emptyTable" } satisfies NodeData,
          leaf: true,
        },
      ];
    }
    return columns.map((column) => ({
      key: `column-${column.id}`,
      data: { type: "column", column } satisfies NodeData,
      leaf: true,
    }));
  }

  const nodes: TreeNode[] = visibleSchemas.map((schema) => {
    const schemaTables = sortedTablesOf(schema.id);
    const children: TreeNode[] =
      schemaTables.length > 0
        ? schemaTables.map((table) => ({
            key: `table-${table.id}`,
            data: { type: "table", table, schemaId: schema.id } satisfies NodeData,
            leaf: false,
            children: tableChildren(table.id),
          }))
        : [
            {
              key: `empty-${schema.id}`,
              data: { type: "emptySchema" } satisfies NodeData,
              leaf: true,
            },
          ];
    return {
      key: `schema-${schema.id}`,
      data: { type: "schema", schema } satisfies NodeData,
      children,
    };
  });

  // Aramada eslesen her schema otomatik acik gosterilir (eski davranisla ayni — bkz.
  // orijinal "expanded = isSearching || expandedSchemaIds.has(...)"), Tree'nin kendi
  // expandedKeys'i bu kaynaklari birlestirerek olusturuluyor.
  const expandedKeys: Record<string, boolean> = {};
  visibleSchemas.forEach((schema) => {
    if (isSearching || expandedSchemaIds.has(schema.id)) {
      expandedKeys[`schema-${schema.id}`] = true;
    }
  });
  expandedTableIds.forEach((id) => {
    expandedKeys[`table-${id}`] = true;
  });

  async function loadColumnsFor(tableId: number) {
    setLoadingTableIds((prev) => new Set(prev).add(tableId));
    try {
      const table = await onLoadColumns(tableId);
      setColumnsByTableId((prev) => new Map(prev).set(tableId, table.columns));
    } finally {
      setLoadingTableIds((prev) => {
        const next = new Set(prev);
        next.delete(tableId);
        return next;
      });
    }
  }

  function handleToggle(value: Record<string, boolean>) {
    const nextSchemaIds = new Set<number>();
    const nextTableIds = new Set<number>();
    Object.keys(value).forEach((key) => {
      if (!value[key]) {
        return;
      }
      if (key.startsWith("schema-")) {
        nextSchemaIds.add(Number(key.slice("schema-".length)));
      } else if (key.startsWith("table-")) {
        nextTableIds.add(Number(key.slice("table-".length)));
      }
    });
    // Yeni acilan (onceden acik olmayan, henuz onbellekte olmayan) her tablo icin kolonlari cek.
    nextTableIds.forEach((tableId) => {
      if (!expandedTableIds.has(tableId) && !columnsByTableId.has(tableId)) {
        loadColumnsFor(tableId);
      }
    });
    setExpandedSchemaIds(nextSchemaIds);
    setExpandedTableIds(nextTableIds);
  }

  /** Sadece kucuk ok'a degil, schema baslik satirinin tamamina (isim/renk noktasi/sayi alanina)
   * tiklayinca da ac/kapa — Tree'nin kendi toggler butonu Edit/Delete'ten ayri, kucuk ve tek
   * basina tiklamasi mantiksiz kaliyordu. */
  function toggleSchema(schemaId: number) {
    setExpandedSchemaIds((prev) => {
      const next = new Set(prev);
      if (next.has(schemaId)) {
        next.delete(schemaId);
      } else {
        next.add(schemaId);
      }
      return next;
    });
  }

  return (
    <aside className="sidebar fadeinup animation-duration-200">
      {canWrite && (
        <div className="sidebar-actions">
          <button className="btn btn-primary" onClick={onCreateClick}>
            {t("sidebar.newTable")}
          </button>
          <button className="btn" onClick={onCreateSchemaClick}>
            {t("sidebar.newSchema")}
          </button>
        </div>
      )}
      {hasAnyTable && (
        <div className="sidebar-search-row">
          <InputText
            type="text"
            className="sidebar-search block"
            placeholder={t("sidebar.searchPlaceholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="sidebar-sort-control relative" title={t("sidebar.sortLabel")}>
            <span className="sidebar-sort-icon absolute" aria-hidden="true">
              &#8645;
            </span>
            <select
              className="sidebar-sort cursor-pointer"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as "name" | "columnCount")}
              aria-label={t("sidebar.sortLabel")}
            >
              <option value="name">{t("sidebar.sortByName")}</option>
              <option value="columnCount">{t("sidebar.sortByKolonCount")}</option>
            </select>
          </div>
        </div>
      )}
      {schemas.length === 0 && <p className="empty-hint">{t("sidebar.empty")}</p>}
      {schemas.length > 0 && isSearching && visibleSchemas.length === 0 && (
        <p className="empty-hint">{t("sidebar.noSearchResults")}</p>
      )}
      {visibleSchemas.length > 0 && (
        <Tree
          value={nodes}
          expandedKeys={expandedKeys}
          onToggle={(e) => handleToggle(e.value as Record<string, boolean>)}
          className="schema-tree"
          nodeTemplate={(node) => {
            const data = node.data as NodeData;

            if (data.type === "emptySchema") {
              return <span className="empty-hint">{t("sidebar.noTablesInSchema")}</span>;
            }

            if (data.type === "emptyTable") {
              return <span className="empty-hint">{t("sidebar.noKolonlarInTablo")}</span>;
            }

            if (data.type === "loadingColumns") {
              return (
                <div
                  role="status"
                  aria-busy="true"
                  aria-label={t("sidebar.loadingKolonlar")}
                  className="sidebar-column-loading"
                >
                  <div className="skeleton skeleton-block" style={{ height: 12, width: "60%" }} />
                  <div className="skeleton skeleton-block" style={{ height: 12, width: "45%" }} />
                </div>
              );
            }

            if (data.type === "column") {
              const column = data.column;
              return (
                <span className="sidebar-column-item w-full">
                  <span
                    className="mono overflow-hidden text-overflow-ellipsis white-space-nowrap"
                    title={column.name}
                  >
                    {column.name}
                  </span>
                  <span className={`type-badge type-badge-${column.type}`}>{column.type}</span>
                  {column.primaryKey && <span className="pk-badge">PK</span>}
                </span>
              );
            }

            if (data.type === "table") {
              const table = data.table;
              return (
                <button
                  className={`table-list-item w-full text-left${table.id === selectedId ? " selected" : ""}${
                    draggedTableId === table.id ? " dragging" : ""
                  }`}
                  onClick={() => onSelect(table.id)}
                  draggable={canWrite}
                  onDragStart={(e) => {
                    // Tree'nin kendi dugumu (li) de bir onDragStart dinleyicisi tasiyor
                    // (dragdropScope hic verilmese bile kosulsuz calisiyor, kaynagina bakinca
                    // event.dataTransfer.setData(...) cagiriyor) — stopPropagation olmadan bu
                    // hem jsdom testlerinde dataTransfer tanimsiz oldugu icin hata firlatiyor
                    // hem de gercek tarayicida bizim surukleme akisimizla alakasiz veri yaziyor.
                    e.stopPropagation();
                    setDraggedTableId(table.id);
                    setDraggedFromSchemaId(data.schemaId);
                    // Bazi ortamlarda (ör. jsdom testleri) dataTransfer tanimsiz olabiliyor —
                    // gercek tarayicida her zaman dolu geliyor ama yine de koruyoruz.
                    if (e.dataTransfer) {
                      e.dataTransfer.effectAllowed = "move";
                    }
                  }}
                  onDragEnd={() => {
                    setDraggedTableId(null);
                    setDraggedFromSchemaId(null);
                  }}
                  title={t("sidebar.dragToMoveHint")}
                >
                  <span
                    className="table-name overflow-hidden text-overflow-ellipsis white-space-nowrap"
                    title={table.name}
                  >
                    {table.name}
                  </span>
                  <span className="column-count">{table.columnCount}</span>
                </button>
              );
            }

            const schema = data.schema;
            const isEditing = editingSchemaId === schema.id;

            if (isEditing) {
              return (
                <form
                  className="inline-edit-form inline-flex"
                  onSubmit={(e) => handleRenameSubmit(e, schema.id)}
                >
                  <input
                    type="text"
                    value={renameDraft}
                    onChange={(e) => setRenameDraft(e.target.value)}
                    autoFocus
                  />
                  <button type="submit" className="btn btn-link">
                    {t("common.save")}
                  </button>
                  <button
                    type="button"
                    className="btn btn-link"
                    onClick={() => setEditingSchemaId(null)}
                  >
                    {t("common.cancel")}
                  </button>
                </form>
              );
            }

            return (
              <div
                className={`schema-header-row${dragOverSchemaId === schema.id ? " drag-over" : ""}`}
                // Drop hedefini schema baslik satirinin tamamina yayiyoruz — eskiden tum
                // schema hucresi (baslik + acikken altindaki tablo listesi) hedefti, Tree'nin
                // dugum yapisinda bize ait olan tek alan bu baslik satiri, o yuzden kapsam
                // biraz daraldi ama hala "tablo adinin tam ustune" gerekmiyor.
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragOverSchemaId(schema.id);
                }}
                onDragLeave={() =>
                  setDragOverSchemaId((prev) => (prev === schema.id ? null : prev))
                }
                onDrop={(e) => handleDropOnSchema(e, schema.id)}
              >
                <span
                  className="schema-header cursor-pointer"
                  role="button"
                  tabIndex={0}
                  onClick={() => toggleSchema(schema.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      toggleSchema(schema.id);
                    }
                  }}
                >
                  <span
                    className="schema-dot"
                    style={{ backgroundColor: schemaColor(schema.name) }}
                    aria-hidden="true"
                  />
                  <span
                    className="mono overflow-hidden text-overflow-ellipsis white-space-nowrap"
                    title={schema.name}
                  >
                    {schema.name}
                  </span>
                  <span className="column-count">{schema.tableCount}</span>
                </span>
                {canWrite && (
                  <button
                    className="btn btn-link"
                    onClick={() => {
                      setRenameDraft(schema.name);
                      setEditingSchemaId(schema.id);
                    }}
                  >
                    {t("common.edit")}
                  </button>
                )}
                {canWrite && (
                  <button
                    className="btn btn-link btn-danger"
                    onClick={async () => {
                      if (
                        await confirm(
                          t("sidebar.confirmDeleteSchema", {
                            name: schema.name,
                            count: schema.tableCount,
                          })
                        )
                      ) {
                        onDeleteSchema(schema.id);
                      }
                    }}
                  >
                    {t("common.delete")}
                  </button>
                )}
              </div>
            );
          }}
        />
      )}
    </aside>
  );
}
