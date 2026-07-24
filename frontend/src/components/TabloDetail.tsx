import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { CreateKolonInput, KOLON_TYPES, Tablo, KolonType } from "../api/tablolar";
import { Tag } from "../api/tags";
import { KolonRow } from "./KolonRow";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";

/** Handler'lar Dashboard'dan geliyor (bkz. Dashboard'daki handle* fonksiyonlari); bu component sadece cagirir, kendisi API'ye dokunmaz. */
interface TabloDetailProps {
  tablo: Tablo;
  tags: Tag[];
  onDeleteTablo: (id: number) => void;
  onRenameTablo: (id: number, name: string) => Promise<void>;
  onAddKolon: (tabloId: number, input: CreateKolonInput) => Promise<void>;
  onDeleteKolon: (tabloId: number, kolonId: number) => void;
  onRenameKolon: (tabloId: number, kolonId: number, name: string) => Promise<void>;
  onChangeKolonTag: (tabloId: number, kolonId: number, tagId: number | null) => Promise<void>;
  onCreateTag: (name: string) => Promise<void>;
}

/**
 * Secili tablonun detay paneli: baslik (duzenlenebilir isim + sil butonu), kolon tablosu,
 * "kolon ekle" ve "tag olustur" formlari hepsi burada. Kendi local UI state'i var
 * (hangi input'un su an duzenlenebilir modda oldugu, form draft'lari) ama gercek veri
 * (tablo, tags) her zaman Dashboard'dan prop olarak gelir.
 */
export function TabloDetail({
  tablo,
  tags,
  onDeleteTablo,
  onRenameTablo,
  onAddKolon,
  onDeleteKolon,
  onRenameKolon,
  onChangeKolonTag,
  onCreateTag,
}: TabloDetailProps) {
  const { t } = useTranslation();
  const [kolonName, setKolonName] = useState("");
  const [kolonType, setKolonType] = useState<KolonType>(KOLON_TYPES[0]);
  // Form gonderilirken butonu disabled yapmak icin — cift tiklamayla ayni istegi iki kez atmayi onler.
  const [submitting, setSubmitting] = useState(false);

  // editingTabloName: "duzenle" moduna gecildi mi. tabloNameDraft: input'taki henuz kaydedilmemis metin.
  const [editingTabloName, setEditingTabloName] = useState(false);
  const [tabloNameDraft, setTabloNameDraft] = useState(tablo.name);

  const [newTagName, setNewTagName] = useState("");

  async function handleAddKolon(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await onAddKolon(tablo.id, { name: kolonName, type: kolonType });
      setKolonName("");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRenameTabloSubmit(event: FormEvent) {
    event.preventDefault();
    await onRenameTablo(tablo.id, tabloNameDraft);
    setEditingTabloName(false);
  }

  async function handleCreateTagSubmit(event: FormEvent) {
    event.preventDefault();
    await onCreateTag(newTagName);
    setNewTagName("");
  }

  return (
    <section className="detail-panel">
      <div className="detail-header">
        {editingTabloName ? (
          <form className="inline-edit-form" onSubmit={handleRenameTabloSubmit}>
            <input
              type="text"
              value={tabloNameDraft}
              onChange={(e) => setTabloNameDraft(e.target.value)}
              autoFocus
            />
            <button type="submit" className="btn btn-link">
              {t("common.save")}
            </button>
            <button
              type="button"
              className="btn btn-link"
              onClick={() => {
                setTabloNameDraft(tablo.name);
                setEditingTabloName(false);
              }}
            >
              {t("common.cancel")}
            </button>
          </form>
        ) : (
          <h2>
            {tablo.name}{" "}
            <button className="btn btn-link" onClick={() => setEditingTabloName(true)}>
              {t("common.edit")}
            </button>
          </h2>
        )}
        <button
          className="btn btn-danger"
          onClick={() => {
            if (window.confirm(t("tabloDetail.confirmDeleteTable", { name: tablo.name }))) {
              onDeleteTablo(tablo.id);
            }
          }}
        >
          {t("tabloDetail.deleteTable")}
        </button>
      </div>

      <table className="kolon-table">
        <thead>
          <tr>
            <th>{t("tabloDetail.colName")}</th>
            <th>{t("tabloDetail.colType")}</th>
            <th>{t("tabloDetail.colTag")}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {tablo.kolonlar.map((kolon) => (
            <KolonRow
              key={kolon.id}
              kolon={kolon}
              tags={tags}
              onRename={(kolonId, name) => onRenameKolon(tablo.id, kolonId, name)}
              onChangeTag={(kolonId, tagId) => onChangeKolonTag(tablo.id, kolonId, tagId)}
              onDelete={(kolonId) => onDeleteKolon(tablo.id, kolonId)}
            />
          ))}
          {tablo.kolonlar.length === 0 && (
            <tr>
              <td colSpan={4} className="empty-hint">
                {t("tabloDetail.emptyColumns")}
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <form className="add-kolon-form" onSubmit={handleAddKolon}>
        <input
          type="text"
          placeholder={t("tabloDetail.columnNamePlaceholder")}
          value={kolonName}
          onChange={(e) => {
            clearCustomValidity(e);
            setKolonName(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          required
        />
        <select value={kolonType} onChange={(e) => setKolonType(e.target.value as KolonType)}>
          {KOLON_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
        <button className="btn" type="submit" disabled={submitting}>
          {t("tabloDetail.addColumn")}
        </button>
      </form>

      <form className="add-tag-form" onSubmit={handleCreateTagSubmit}>
        <input
          type="text"
          placeholder={t("tabloDetail.tagNamePlaceholder")}
          value={newTagName}
          onChange={(e) => {
            clearCustomValidity(e);
            setNewTagName(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          required
        />
        <button className="btn" type="submit">
          {t("tabloDetail.createTag")}
        </button>
      </form>
    </section>
  );
}
