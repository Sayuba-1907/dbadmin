import { DragEvent, FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Schema } from "../api/schemas";
import { Tablo } from "../api/tablolar";
import { useAuth } from "../auth/AuthProvider";

/**
 * Props: gercek veri (schemalar, tablolar, selectedId) ve "bir seye tikladiginda ne olsun"
 * callback'leri hep Dashboard'dan gelir. Bu component'in kendi tuttugu state, arama kutusunun
 * metni, hangi schema'larin acik (expanded) oldugu ve hangi schema su an yeniden adlandirma
 * modunda oldugu — hepsi salt gorsel/gecici UI durumu, Dashboard'un ya da backend'in hic
 * umrunda degil.
 */
interface TabloSidebarProps {
  schemalar: Schema[];
  tablolar: Tablo[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreateClick: () => void;
  onCreateSchemaClick: () => void;
  onRenameSchema: (id: number, name: string) => Promise<void>;
  onDeleteSchema: (id: number) => void;
  onChangeTabloSchema: (id: number, schemaId: number) => Promise<void>;
}

/** Sol tarafta schema -> tablo hiyerarsisi + "yeni tablo"/"yeni schema" butonlari + arama kutusu. */
export function TabloSidebar({
  schemalar,
  tablolar,
  selectedId,
  onSelect,
  onCreateClick,
  onCreateSchemaClick,
  onRenameSchema,
  onDeleteSchema,
  onChangeTabloSchema,
}: TabloSidebarProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const [query, setQuery] = useState("");
  const [expandedSchemaIds, setExpandedSchemaIds] = useState<Set<number>>(new Set());
  const [editingSchemaId, setEditingSchemaId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");
  // Surukle-birak sirasinda hangi tablo tasiniyor ve hangi schema basligi hedef olarak
  // vurgulanmali — ikisi de salt gorsel/gecici state, backend'in umrunda degil.
  const [draggedTabloId, setDraggedTabloId] = useState<number | null>(null);
  const [dragOverSchemaId, setDragOverSchemaId] = useState<number | null>(null);
  // Her schema'nin altindaki tablo listesinin hangi kritere gore siralanacagi — sadece
  // gorsel/gecici UI durumu, backend'e hic gitmiyor (backend zaten isme gore sirali doner,
  // "kolon sayisi"/"son guncelleme" secenekleri tamamen frontend'de, elimizdeki veri
  // uzerinde hesaplaniyor).
  const [sortBy, setSortBy] = useState<"name" | "kolonCount" | "updatedAt">("name");

  /** Bir tablo, uzerine birakildigi schema'nin basligina surukle-birakla tasinir. Zaten o schema'daysa hicbir sey yapmaz. */
  function handleDropOnSchema(event: DragEvent, targetSchemaId: number) {
    event.preventDefault();
    setDragOverSchemaId(null);
    const tabloId = draggedTabloId;
    setDraggedTabloId(null);
    if (tabloId == null) {
      return;
    }
    const draggedTablo = tablolar.find((tbl) => tbl.id === tabloId);
    if (draggedTablo && draggedTablo.schemaId !== targetSchemaId) {
      onChangeTabloSchema(tabloId, targetSchemaId);
    }
  }

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

  async function handleRenameSubmit(event: FormEvent, schemaId: number) {
    event.preventDefault();
    await onRenameSchema(schemaId, renameDraft);
    setEditingSchemaId(null);
  }

  // Buyuk/kucuk harf duyarsiz, basit bir "icinde geciyor mu" filtresi — backend'e hic gitmiyor,
  // tamamen zaten ekranda olan listenin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const isSearching = normalizedQuery !== "";
  const filteredTablolar = isSearching
    ? tablolar.filter((tablo) => tablo.name.toLowerCase().includes(normalizedQuery))
    : tablolar;

  // "kolonCount" -> en cok kolonlu tablo en ustte (azalan, b - a).
  // "updatedAt" -> en son degisen tablo en ustte (azalan); updatedAt null ise (eski satirlar)
  // en sona atilir (epoch = 0 kabul edilir).
  // "name" -> Turkce alfabetik siraya gore (localeCompare "tr").
  const sortedTablolar = [...filteredTablolar].sort((a, b) => {
    if (sortBy === "kolonCount") {
      return b.kolonlar.length - a.kolonlar.length;
    }
    if (sortBy === "updatedAt") {
      const aTime = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
      const bTime = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
      return bTime - aTime;
    }
    return a.name.localeCompare(b.name, "tr");
  });

  const tablolarBySchemaId = new Map<number, Tablo[]>();
  for (const tablo of sortedTablolar) {
    const list = tablolarBySchemaId.get(tablo.schemaId) ?? [];
    list.push(tablo);
    tablolarBySchemaId.set(tablo.schemaId, list);
  }

  // Arama yaparken bir schema'nin altinda hic eslesme yoksa o schema'yi tamamen gizliyoruz;
  // aramiyorsak (bos schema dahil) her schema gorunur — kullanici yeni actigi bos bir schema'yi
  // "kayboldu" sanmasin diye.
  const visibleSchemalar = isSearching
    ? schemalar.filter((schema) => (tablolarBySchemaId.get(schema.id) ?? []).length > 0)
    : schemalar;

  return (
    <aside className="sidebar">
      <div className="sidebar-actions">
        <button className="btn btn-primary" onClick={onCreateClick} disabled={!canWrite}>
          {t("sidebar.newTable")}
        </button>
        <button className="btn" onClick={onCreateSchemaClick} disabled={!canWrite}>
          {t("sidebar.newSchema")}
        </button>
      </div>
      {tablolar.length > 0 && (
        <div className="sidebar-search-row">
          <input
            type="text"
            className="sidebar-search"
            placeholder={t("sidebar.searchPlaceholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="sidebar-sort-control" title={t("sidebar.sortLabel")}>
            <span className="sidebar-sort-icon" aria-hidden="true">
              &#8645;
            </span>
            <select
              className="sidebar-sort"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as "name" | "kolonCount" | "updatedAt")}
              aria-label={t("sidebar.sortLabel")}
            >
              <option value="name">{t("sidebar.sortByName")}</option>
              <option value="kolonCount">{t("sidebar.sortByKolonCount")}</option>
              <option value="updatedAt">{t("sidebar.sortByUpdatedAt")}</option>
            </select>
          </div>
        </div>
      )}
      <ul className="schema-list">
        {visibleSchemalar.map((schema) => {
          const schemaTablolar = tablolarBySchemaId.get(schema.id) ?? [];
          const expanded = isSearching || expandedSchemaIds.has(schema.id);
          // Burada listelenen her schema kullanicinin kendi olusturdugu bir schema; hepsi
          // yeniden adlandirilabilir ve silinebilir. Altyapiya ait "public" backend tarafindan
          // ayiklandigi icin (bkz. SchemaService.RESERVED_SCHEMA_NAME) buraya hic gelmez.
          const isEditing = editingSchemaId === schema.id;

          return (
            <li
              key={schema.id}
              className={`schema-group${dragOverSchemaId === schema.id ? " drag-over" : ""}`}
              // Drop hedefini sadece baslik satirina degil, schema'nin tum alanina (baslik +
              // acikken altinda gorunen tablo listesi) yayiyoruz — kullanicinin tabloyu tam
              // baslik satirinin uzerine birakmasi gerekmesin, o schema'nin herhangi bir
              // yerine birakmasi yeterli olsun diye.
              onDragOver={(e) => {
                e.preventDefault();
                setDragOverSchemaId(schema.id);
              }}
              onDragLeave={() => setDragOverSchemaId((prev) => (prev === schema.id ? null : prev))}
              onDrop={(e) => handleDropOnSchema(e, schema.id)}
            >
              <div className="schema-header-row">
                {isEditing ? (
                  <form
                    className="inline-edit-form"
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
                ) : (
                  <>
                    <button className="schema-header" onClick={() => toggleSchema(schema.id)}>
                      <span className={`schema-caret${expanded ? " expanded" : ""}`}>&#9656;</span>
                      {schema.name}
                      <span className="kolon-count">{schemaTablolar.length}</span>
                    </button>
                    <button
                      className="btn btn-link"
                      disabled={!canWrite}
                      onClick={() => {
                        setRenameDraft(schema.name);
                        setEditingSchemaId(schema.id);
                      }}
                    >
                      {t("common.edit")}
                    </button>
                    <button
                      className="btn btn-link btn-danger"
                      disabled={!canWrite}
                      onClick={() => {
                        if (
                          window.confirm(
                            t("sidebar.confirmDeleteSchema", {
                              name: schema.name,
                              count: schemaTablolar.length,
                            })
                          )
                        ) {
                          onDeleteSchema(schema.id);
                        }
                      }}
                    >
                      {t("common.delete")}
                    </button>
                  </>
                )}
              </div>
              {expanded && (
                <ul className="tablo-list">
                  {schemaTablolar.map((tablo) => (
                    <li key={tablo.id}>
                      <button
                        className={`tablo-list-item${tablo.id === selectedId ? " selected" : ""}${
                          draggedTabloId === tablo.id ? " dragging" : ""
                        }`}
                        onClick={() => onSelect(tablo.id)}
                        draggable={canWrite}
                        onDragStart={(e) => {
                          setDraggedTabloId(tablo.id);
                          // Bazi ortamlarda (ör. jsdom testleri) dataTransfer tanimsiz olabiliyor —
                          // gercek tarayicida her zaman dolu geliyor ama yine de koruyoruz.
                          if (e.dataTransfer) {
                            e.dataTransfer.effectAllowed = "move";
                          }
                        }}
                        onDragEnd={() => setDraggedTabloId(null)}
                        title={t("sidebar.dragToMoveHint")}
                      >
                        {tablo.name}
                        <span className="kolon-count">{tablo.kolonlar.length}</span>
                      </button>
                    </li>
                  ))}
                  {schemaTablolar.length === 0 && (
                    <li className="empty-hint">{t("sidebar.noTablesInSchema")}</li>
                  )}
                </ul>
              )}
            </li>
          );
        })}
        {schemalar.length === 0 && <li className="empty-hint">{t("sidebar.empty")}</li>}
        {schemalar.length > 0 && isSearching && visibleSchemalar.length === 0 && (
          <li className="empty-hint">{t("sidebar.noSearchResults")}</li>
        )}
      </ul>
    </aside>
  );
}
