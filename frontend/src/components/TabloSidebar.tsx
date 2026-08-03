import { DragEvent, FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Schema, TabloSummary } from "../api/schemas";
import { useAuth } from "../auth/AuthProvider";

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

/**
 * Props: gercek veri (schemalar, her schema'nin tablo ozetleri, selectedId) ve "bir seye
 * tikladiginda ne olsun" callback'leri hep Dashboard'dan gelir. Bu component'in kendi tuttugu
 * state, arama kutusunun metni, hangi schema'larin acik (expanded) oldugu ve hangi schema su an
 * yeniden adlandirma modunda oldugu — hepsi salt gorsel/gecici UI durumu, Dashboard'un ya da
 * backend'in hic umrunda degil.
 * <p>
 * Her schema'nin altindaki tablo listesi TabloSummary (sadece id/name/kolonSayisi) — tablonun
 * kolonlarinin tam listesi burada YOK, bir tabloya tiklaninca Dashboard onu ayrica id'siyle
 * (GET /api/tablolar/{id}) ceker.
 */
interface TabloSidebarProps {
  schemalar: Schema[];
  tabloSummariesBySchema: Record<number, TabloSummary[]>;
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
  tabloSummariesBySchema,
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
  // Surukle-birak sirasinda hangi tablo hangi schema'dan tasiniyor ve hangi schema basligi
  // hedef olarak vurgulanmali — hepsi salt gorsel/gecici state, backend'in umrunda degil.
  // draggedFromSchemaId ayrica tutuluyor cunku TabloSummary'de schemaId alani yok (sadece
  // id/name/kolonSayisi) — tablonun su an hangi schema'da oldugunu sadece hangi listeden
  // suruklendiginden biliyoruz.
  const [draggedTabloId, setDraggedTabloId] = useState<number | null>(null);
  const [draggedFromSchemaId, setDraggedFromSchemaId] = useState<number | null>(null);
  const [dragOverSchemaId, setDragOverSchemaId] = useState<number | null>(null);
  // Her schema'nin altindaki tablo listesinin hangi kritere gore siralanacagi — sadece
  // gorsel/gecici UI durumu, backend'e hic gitmiyor (backend zaten isme gore sirali doner,
  // "kolon sayisi" secenegi tamamen frontend'de, elimizdeki ozet veri uzerinde hesaplaniyor).
  const [sortBy, setSortBy] = useState<"name" | "kolonCount">("name");

  /** Bir tablo, uzerine birakildigi schema'nin basligina surukle-birakla tasinir. Zaten o schema'daysa hicbir sey yapmaz. */
  function handleDropOnSchema(event: DragEvent, targetSchemaId: number) {
    event.preventDefault();
    setDragOverSchemaId(null);
    const tabloId = draggedTabloId;
    const fromSchemaId = draggedFromSchemaId;
    setDraggedTabloId(null);
    setDraggedFromSchemaId(null);
    if (tabloId == null) {
      return;
    }
    if (fromSchemaId !== targetSchemaId) {
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
  // tamamen zaten ekranda olan (yuklenmis) ozet listelerin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const isSearching = normalizedQuery !== "";

  function sortedTablolarOf(schemaId: number): TabloSummary[] {
    const all = tabloSummariesBySchema[schemaId] ?? [];
    const filtered = isSearching
      ? all.filter((tablo) => tablo.name.toLowerCase().includes(normalizedQuery))
      : all;
    // "kolonCount" -> en cok kolonlu tablo en ustte (azalan, b - a).
    // "name" -> Turkce alfabetik siraya gore (localeCompare "tr").
    return [...filtered].sort((a, b) =>
      sortBy === "kolonCount" ? b.kolonSayisi - a.kolonSayisi : a.name.localeCompare(b.name, "tr")
    );
  }

  const hasAnyTablo = Object.values(tabloSummariesBySchema).some((list) => list.length > 0);

  // Arama yaparken bir schema'nin altinda hic eslesme yoksa o schema'yi tamamen gizliyoruz;
  // aramiyorsak (bos schema dahil) her schema gorunur — kullanici yeni actigi bos bir schema'yi
  // "kayboldu" sanmasin diye.
  const visibleSchemalar = isSearching
    ? schemalar.filter((schema) => sortedTablolarOf(schema.id).length > 0)
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
      {hasAnyTablo && (
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
              onChange={(e) => setSortBy(e.target.value as "name" | "kolonCount")}
              aria-label={t("sidebar.sortLabel")}
            >
              <option value="name">{t("sidebar.sortByName")}</option>
              <option value="kolonCount">{t("sidebar.sortByKolonCount")}</option>
            </select>
          </div>
        </div>
      )}
      <ul className="schema-list">
        {visibleSchemalar.map((schema) => {
          const schemaTablolar = sortedTablolarOf(schema.id);
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
                      <span
                        className="schema-dot"
                        style={{ backgroundColor: schemaColor(schema.name) }}
                        aria-hidden="true"
                      />
                      <span className="mono">{schema.name}</span>
                      <span className="kolon-count">{schema.tabloSayisi}</span>
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
                              count: schema.tabloSayisi,
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
                          setDraggedFromSchemaId(schema.id);
                          // Bazi ortamlarda (ör. jsdom testleri) dataTransfer tanimsiz olabiliyor —
                          // gercek tarayicida her zaman dolu geliyor ama yine de koruyoruz.
                          if (e.dataTransfer) {
                            e.dataTransfer.effectAllowed = "move";
                          }
                        }}
                        onDragEnd={() => {
                          setDraggedTabloId(null);
                          setDraggedFromSchemaId(null);
                        }}
                        title={t("sidebar.dragToMoveHint")}
                      >
                        {tablo.name}
                        <span className="kolon-count">{tablo.kolonSayisi}</span>
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
