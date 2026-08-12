import { DragEvent, FormEvent, memo, useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { List, RowComponentProps } from "react-window";
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

// Sabit satir yuksekligi (react-window'un FixedSize modu) — CSS'teki padding'ler bu deger etrafinda
// ayarlandi (bkz. App.css .schema-header/.table-list-item/.sidebar-column-item). Bir schema binlerce
// tablo tasiyabildigi icin (bkz. asagidaki javadoc) sabit yukseklik, degisken yukseklik olcumunden
// (ResizeObserver ile her satiri olcmek) cok daha ucuz.
const ROW_HEIGHT = 32;

/**
 * Sidebar'daki agac (schema -> tablo -> kolon) DUZ bir listeye "cokertilip" react-window ile
 * render ediliyor — bkz. asagidaki TableSidebar javadoc'u: PrimeReact'in Tree bileseninde HICBIR
 * surumde virtualization yok (kontrol edildi: 10.9.8 ve 11.1.0), yani binlerce tablolu bir
 * schema acildiginda TUM satirlar DOM'a basiliyordu — saniyelerce donmaya yol aciyordu. Bu
 * FlatRow modeli sadece o an GORUNUR olan (ekrandaki + kucuk bir tampon) satirlarin gercekten
 * DOM'da olmasini saglar; geri kalan binlercesi hic mount edilmez.
 */
type FlatRow =
  | { kind: "schema"; schema: Schema }
  | { kind: "emptySchema"; schemaId: number }
  | { kind: "table"; table: TableSummary; schemaId: number }
  | { kind: "emptyTable"; tableId: number }
  | { kind: "loadingColumns"; tableId: number }
  | { kind: "column"; column: Column; tableId: number };

/** SidebarRow'un (asagida) ihtiyac duydugu her sey — react-window'un rowProps'una tek parca halinde geciyor. */
interface SidebarRowProps {
  rows: FlatRow[];
  selectedId: number | null;
  draggedTableId: number | null;
  dragOverSchemaId: number | null;
  editingSchemaId: number | null;
  renameDraft: string;
  canWrite: boolean;
  expandedSchemaIds: Set<number>;
  expandedTableIds: Set<number>;
  isSearching: boolean;
  t: (key: string, options?: Record<string, unknown>) => string;
  confirm: (message: string) => Promise<boolean>;
  onSelect: (id: number) => void;
  onDeleteSchema: (id: number) => void;
  toggleSchema: (schemaId: number) => void;
  toggleTable: (tableId: number) => void;
  setDraggedTableId: (id: number | null) => void;
  setDraggedFromSchemaId: (id: number | null) => void;
  setDragOverSchemaId: (id: number | null) => void;
  setEditingSchemaId: (id: number | null) => void;
  setRenameDraft: (value: string) => void;
  handleDropOnSchema: (event: DragEvent, schemaId: number) => void;
  handleRenameSubmit: (event: FormEvent, schemaId: number) => void;
}

/**
 * react-window her satir icin bu bileseni cagirir — SADECE o an gorunur olanlar icin (bkz.
 * yukaridaki FlatRow javadoc'u). {@link memo} yine de faydali: scroll sirasinda react-window
 * ayni satiri farkli bir index'e tasiyabiliyor, props degismediyse (nadiren) tekrar render
 * etmeye gerek yok.
 */
const SidebarRow = memo(function SidebarRow({
  index,
  style,
  rows,
  selectedId,
  draggedTableId,
  dragOverSchemaId,
  editingSchemaId,
  renameDraft,
  canWrite,
  expandedSchemaIds,
  expandedTableIds,
  isSearching,
  t,
  confirm,
  onSelect,
  onDeleteSchema,
  toggleSchema,
  toggleTable,
  setDraggedTableId,
  setDraggedFromSchemaId,
  setDragOverSchemaId,
  setEditingSchemaId,
  setRenameDraft,
  handleDropOnSchema,
  handleRenameSubmit,
}: RowComponentProps<SidebarRowProps>) {
  const row = rows[index];

  if (row.kind === "emptySchema") {
    return (
      <div style={style} className="sidebar-row" data-depth={1}>
        <span className="empty-hint">{t("sidebar.noTablesInSchema")}</span>
      </div>
    );
  }

  if (row.kind === "emptyTable") {
    return (
      <div style={style} className="sidebar-row" data-depth={2}>
        <span className="empty-hint">{t("sidebar.noKolonlarInTablo")}</span>
      </div>
    );
  }

  if (row.kind === "loadingColumns") {
    return (
      <div
        style={style}
        className="sidebar-row sidebar-column-loading"
        data-depth={2}
        role="status"
        aria-busy="true"
        aria-label={t("sidebar.loadingKolonlar")}
      >
        <div className="skeleton skeleton-block" style={{ height: 12, width: "60%" }} />
      </div>
    );
  }

  if (row.kind === "column") {
    const column = row.column;
    return (
      <div style={style} className="sidebar-row" data-depth={2}>
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
      </div>
    );
  }

  if (row.kind === "table") {
    const table = row.table;
    const isExpanded = expandedTableIds.has(table.id);
    return (
      <div style={style} className="sidebar-row" data-depth={1}>
        <button
          type="button"
          className="sidebar-toggler"
          aria-expanded={isExpanded}
          aria-label={t("sidebar.dragToMoveHint")}
          onClick={() => toggleTable(table.id)}
        >
          {isExpanded ? "▾" : "▸"}
        </button>
        <button
          className={`table-list-item w-full text-left${
            table.id === selectedId ? " selected" : ""
          }${draggedTableId === table.id ? " dragging" : ""}`}
          onClick={() => onSelect(table.id)}
          draggable={canWrite}
          onDragStart={(e) => {
            e.stopPropagation();
            setDraggedTableId(table.id);
            setDraggedFromSchemaId(row.schemaId);
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
      </div>
    );
  }

  const schema = row.schema;
  const isEditing = editingSchemaId === schema.id;
  const isExpanded = isSearching || expandedSchemaIds.has(schema.id);

  if (isEditing) {
    return (
      <div style={style} className="sidebar-row" data-depth={0}>
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
          <button type="button" className="btn btn-link" onClick={() => setEditingSchemaId(null)}>
            {t("common.cancel")}
          </button>
        </form>
      </div>
    );
  }

  return (
    <div
      style={style}
      className={`sidebar-row schema-header-row${
        dragOverSchemaId === schema.id ? " drag-over" : ""
      }`}
      data-depth={0}
      onDragOver={(e) => {
        e.preventDefault();
        setDragOverSchemaId(schema.id);
      }}
      onDragLeave={() => {
        if (dragOverSchemaId === schema.id) {
          setDragOverSchemaId(null);
        }
      }}
      onDrop={(e) => handleDropOnSchema(e, schema.id)}
    >
      <button
        type="button"
        className="sidebar-toggler"
        aria-expanded={isExpanded}
        onClick={() => toggleSchema(schema.id)}
      >
        {isExpanded ? "▾" : "▸"}
      </button>
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
                t("sidebar.confirmDeleteSchema", { name: schema.name, count: schema.tableCount })
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
});

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
 * lazy olarak {@link onLoadColumns} ile ceker ve {@code columnsByTableId}'de onbelleklenir —
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
 * Agac gorunumu {@link FlatRow} ile duz bir listeye cevrilip react-window'un {@code List}'iyle
 * sanallastirilarak render edilir (bkz. FlatRow javadoc'u — PrimeReact Tree'de virtualization
 * hic yok). Surukle-birak, yeniden adlandirma formu, renk noktasi gibi her sey kendi state'imizle
 * yonetiliyor (SidebarRow'da).
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
  // Agacta bir tablo genisletilince (bkz. toggleTable) kolonlari lazy cekilir ve burada
  // onbelleklenir — ayni tabloyu tekrar ac/kapa yapmak ikinci bir istek atmaz (TaglerPanel'in
  // kullanim onbellegiyle ayni desen).
  const [expandedTableIds, setExpandedTableIds] = useState<Set<number>>(new Set());
  const [columnsByTableId, setColumnsByTableId] = useState<Map<number, Column[]>>(new Map());
  const [loadingTableIds, setLoadingTableIds] = useState<Set<number>>(new Set());
  // Surukle-birak sirasinda hangi tablo hangi schema'dan tasiniyor ve hangi schema basligi
  // hedef olarak vurgulanmali — hepsi salt gorsel/gecici state, backend'in umrunde degil.
  const [draggedTableId, setDraggedTableId] = useState<number | null>(null);
  const [draggedFromSchemaId, setDraggedFromSchemaId] = useState<number | null>(null);
  const [dragOverSchemaId, setDragOverSchemaId] = useState<number | null>(null);
  // Her schema'nin altindaki tablo listesinin hangi kritere gore siralanacagi — sadece
  // gorsel/gecici UI durumu, backend'e hic gitmiyor.
  const [sortBy, setSortBy] = useState<"name" | "columnCount">("name");

  const handleDropOnSchema = useCallback(
    (event: DragEvent, targetSchemaId: number) => {
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
    },
    [draggedTableId, draggedFromSchemaId, onChangeTableSchema]
  );

  const handleRenameSubmit = useCallback(
    async (event: FormEvent, schemaId: number) => {
      event.preventDefault();
      await onRenameSchema(schemaId, renameDraft);
      setEditingSchemaId(null);
    },
    [onRenameSchema, renameDraft]
  );

  // Buyuk/kucuk harf duyarsiz, basit bir "icinde geciyor mu" filtresi — backend'e hic gitmiyor,
  // tamamen zaten ekranda olan (yuklenmis) ozet listelerin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const isSearching = normalizedQuery !== "";

  const sortedTablesOf = useCallback(
    (schemaId: number): TableSummary[] => {
      const all = tableSummariesBySchema[schemaId] ?? [];
      const filtered = isSearching
        ? all.filter((table) => table.name.toLowerCase().includes(normalizedQuery))
        : all;
      return [...filtered].sort((a, b) =>
        sortBy === "columnCount"
          ? b.columnCount - a.columnCount
          : a.name.localeCompare(b.name, "tr")
      );
    },
    [tableSummariesBySchema, isSearching, normalizedQuery, sortBy]
  );

  const hasAnyTable = Object.values(tableSummariesBySchema).some((list) => list.length > 0);

  const visibleSchemas = isSearching
    ? schemas.filter((schema) => sortedTablesOf(schema.id).length > 0)
    : schemas;

  const loadColumnsFor = useCallback(
    async (tableId: number) => {
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
    },
    [onLoadColumns]
  );

  const toggleSchema = useCallback((schemaId: number) => {
    setExpandedSchemaIds((prev) => {
      const next = new Set(prev);
      if (next.has(schemaId)) {
        next.delete(schemaId);
      } else {
        next.add(schemaId);
      }
      return next;
    });
  }, []);

  const toggleTable = useCallback(
    (tableId: number) => {
      setExpandedTableIds((prev) => {
        const next = new Set(prev);
        if (next.has(tableId)) {
          next.delete(tableId);
        } else {
          next.add(tableId);
        }
        return next;
      });
      setColumnsByTableId((prevColumns) => {
        if (!prevColumns.has(tableId)) {
          loadColumnsFor(tableId);
        }
        return prevColumns;
      });
    },
    [loadColumnsFor]
  );

  // Agac (schema -> tablo -> kolon) burada duz bir listeye "cokertiliyor" — bkz. FlatRow
  // javadoc'u. Sadece bu dizinin BOYUTU degil, react-window sayesinde o an gorunmeyen satirlar
  // hic React elemanina/DOM dugumune donusmuyor.
  const rows: FlatRow[] = useMemo(() => {
    const result: FlatRow[] = [];
    visibleSchemas.forEach((schema) => {
      result.push({ kind: "schema", schema });
      const expanded = isSearching || expandedSchemaIds.has(schema.id);
      if (!expanded) {
        return;
      }
      const tables = sortedTablesOf(schema.id);
      if (tables.length === 0) {
        result.push({ kind: "emptySchema", schemaId: schema.id });
        return;
      }
      tables.forEach((table) => {
        result.push({ kind: "table", table, schemaId: schema.id });
        if (!expandedTableIds.has(table.id)) {
          return;
        }
        if (loadingTableIds.has(table.id)) {
          result.push({ kind: "loadingColumns", tableId: table.id });
          return;
        }
        const columns = columnsByTableId.get(table.id);
        if (!columns) {
          return;
        }
        if (columns.length === 0) {
          result.push({ kind: "emptyTable", tableId: table.id });
          return;
        }
        columns.forEach((column) => {
          result.push({ kind: "column", column, tableId: table.id });
        });
      });
    });
    return result;
  }, [
    visibleSchemas,
    isSearching,
    expandedSchemaIds,
    expandedTableIds,
    loadingTableIds,
    columnsByTableId,
    sortedTablesOf,
  ]);

  const rowProps: SidebarRowProps = useMemo(
    () => ({
      rows,
      selectedId,
      draggedTableId,
      dragOverSchemaId,
      editingSchemaId,
      renameDraft,
      canWrite,
      expandedSchemaIds,
      expandedTableIds,
      isSearching,
      t,
      confirm,
      onSelect,
      onDeleteSchema,
      toggleSchema,
      toggleTable,
      setDraggedTableId,
      setDraggedFromSchemaId,
      setDragOverSchemaId,
      setEditingSchemaId,
      setRenameDraft,
      handleDropOnSchema,
      handleRenameSubmit,
    }),
    [
      rows,
      selectedId,
      draggedTableId,
      dragOverSchemaId,
      editingSchemaId,
      renameDraft,
      canWrite,
      expandedSchemaIds,
      expandedTableIds,
      isSearching,
      t,
      confirm,
      onSelect,
      onDeleteSchema,
      toggleSchema,
      toggleTable,
      handleDropOnSchema,
      handleRenameSubmit,
    ]
  );

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
      {rows.length > 0 && (
        <div className="schema-tree-scroll">
          <List
            rowComponent={SidebarRow}
            rowCount={rows.length}
            rowHeight={ROW_HEIGHT}
            rowProps={rowProps}
            className="schema-tree"
            style={{ height: "100%", width: "100%" }}
          />
        </div>
      )}
    </aside>
  );
}
