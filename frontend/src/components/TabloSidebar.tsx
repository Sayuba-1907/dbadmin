import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Schema } from "../api/schemas";
import { Tablo } from "../api/tablolar";

const PUBLIC_SCHEMA_NAME = "public";

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
}: TabloSidebarProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [expandedSchemaIds, setExpandedSchemaIds] = useState<Set<number>>(new Set());
  const [editingSchemaId, setEditingSchemaId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");

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

  const tablolarBySchemaId = new Map<number, Tablo[]>();
  for (const tablo of filteredTablolar) {
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
        <button className="btn btn-primary" onClick={onCreateClick}>
          {t("sidebar.newTable")}
        </button>
        <button className="btn" onClick={onCreateSchemaClick}>
          {t("sidebar.newSchema")}
        </button>
      </div>
      {tablolar.length > 0 && (
        <input
          type="text"
          className="sidebar-search"
          placeholder={t("sidebar.searchPlaceholder")}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      )}
      <ul className="schema-list">
        {visibleSchemalar.map((schema) => {
          const schemaTablolar = tablolarBySchemaId.get(schema.id) ?? [];
          const expanded = isSearching || expandedSchemaIds.has(schema.id);
          // "public" sistemin varsayilan schema'si (bkz. backend SchemaBootstrapRunner) — silinemez/yeniden
          // adlandirilamaz, o yuzden bu butonlari onun icin hic gostermiyoruz (backend zaten reddediyor,
          // burada sadece kullaniciya bosuna basarisiz olacak bir buton sunmuyoruz).
          const isProtected = schema.name.toLowerCase() === PUBLIC_SCHEMA_NAME;
          const isEditing = editingSchemaId === schema.id;

          return (
            <li key={schema.id} className="schema-group">
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
                    {!isProtected && (
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
                    {!isProtected && (
                      <button
                        className="btn btn-link btn-danger"
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
                    )}
                  </>
                )}
              </div>
              {expanded && (
                <ul className="tablo-list">
                  {schemaTablolar.map((tablo) => (
                    <li key={tablo.id}>
                      <button
                        className={`tablo-list-item${tablo.id === selectedId ? " selected" : ""}`}
                        onClick={() => onSelect(tablo.id)}
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
