import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { DraftKolon, KOLON_TYPES, KolonType, TabloDraft } from "../api/tablolar";
import { Tag } from "../api/tags";
import { KolonRow } from "./KolonRow";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../notifications/ConfirmProvider";

/**
 * Handler'lar Dashboard'dan geliyor. Buradaki tum onChange* callback'leri SENKRON — hicbiri
 * API'ye gitmez, hepsi Dashboard'daki taslagi (draft) gunceller. Tek asenkron olan {@code onSave}:
 * o da taslak ile orijinal tablo arasindaki farki TEK istekte gonderir (bkz.
 * Dashboard.handleSaveDraft / TabloService.applyChanges).
 */
interface TabloDetailProps {
  draft: TabloDraft;
  tags: Tag[];
  isDirty: boolean;
  saving: boolean;
  /** Sürüklenerek bekleyen bir schema taşıması varsa hedef schema'nın adı, yoksa null. */
  pendingSchemaName: string | null;
  onChangeName: (name: string) => void;
  onAddKolon: (input: {
    name: string;
    type: KolonType;
    tagId: number | null;
    primaryKey: boolean;
  }) => void;
  onToggleDeleteKolon: (kolonId: number) => void;
  onChangeKolonName: (kolonId: number, name: string) => void;
  onChangeKolonTag: (kolonId: number, tagId: number | null) => void;
  onChangeKolonPrimaryKey: (kolonId: number, primaryKey: boolean) => void;
  onSave: () => Promise<void>;
  onDiscard: () => void;
  onDeleteTablo: (id: number) => void;
  onCreateTag: (name: string) => Promise<void>;
}

/**
 * Secili tablonun detay paneli: baslik (duzenlenebilir isim), kolon tablosu, "kolon ekle" ve
 * "tag olustur" formlari hepsi burada. "Tabloyu sil" HARIC hicbir aksiyon aninda API'ye gitmez
 * — hepsi {@code draft}'i gunceller, en sonda "Kaydet"e basinca Dashboard tek bir istekte
 * gonderir. Bu yuzden butun input'lar dogrudan {@code draft.*}'a baglidir (kontrollu/controlled),
 * kendi ayri bir "duzenleme modu" tutmazlar.
 */
export function TabloDetail({
  draft,
  tags,
  isDirty,
  saving,
  pendingSchemaName,
  onChangeName,
  onAddKolon,
  onToggleDeleteKolon,
  onChangeKolonName,
  onChangeKolonTag,
  onChangeKolonPrimaryKey,
  onSave,
  onDiscard,
  onDeleteTablo,
  onCreateTag,
}: TabloDetailProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const confirm = useConfirm();
  const [kolonName, setKolonName] = useState("");
  const [kolonType, setKolonType] = useState<KolonType>(KOLON_TYPES[0]);
  const [kolonPrimaryKey, setKolonPrimaryKey] = useState(false);
  const [newTagName, setNewTagName] = useState("");

  function handleAddKolonSubmit(event: FormEvent) {
    event.preventDefault();
    onAddKolon({ name: kolonName, type: kolonType, tagId: null, primaryKey: kolonPrimaryKey });
    setKolonName("");
    setKolonPrimaryKey(false);
  }

  async function handleCreateTagSubmit(event: FormEvent) {
    event.preventDefault();
    await onCreateTag(newTagName);
    setNewTagName("");
  }

  return (
    <section className="detail-panel">
      {/* VIEWER rolu icin tum yazma kontrollerini tek yerden kapatir — fieldset'in disabled'i
          DOM derinligi fark etmeksizin her input/select/button torununa uygulanir, yani
          KolonRow'un icindeki tekil kontrollere ayrica dokunmaya gerek yok. Backend zaten
          403 donuyor (bkz. SecurityConfig); bu sadece kullanicinin yapamayacagi bir seyi
          denemesini engelleyen bir UX katmani. */}
      <fieldset className="unstyled-fieldset" disabled={!canWrite}>
        <div className="detail-header">
          <input
            type="text"
            className="tablo-name-input"
            value={draft.name}
            onChange={(e) => onChangeName(e.target.value)}
          />
          {pendingSchemaName && (
            <span className="pending-move-hint">
              {t("tabloDetail.pendingSchemaMove", { schema: pendingSchemaName })}
            </span>
          )}
          <div className="detail-header-actions">
            {isDirty && (
              <>
                <button className="btn btn-link" onClick={onDiscard} disabled={saving}>
                  {t("common.cancel")}
                </button>
                <button className="btn btn-primary" onClick={onSave} disabled={saving}>
                  {t("common.save")}
                </button>
              </>
            )}
            {canWrite && (
              <button
                className="btn btn-danger"
                onClick={async () => {
                  if (await confirm(t("tabloDetail.confirmDeleteTable", { name: draft.name }))) {
                    onDeleteTablo(draft.tabloId);
                  }
                }}
              >
                {t("tabloDetail.deleteTable")}
              </button>
            )}
          </div>
        </div>

        <div className="detail-card">
          <table className="kolon-table">
            <thead>
              <tr>
                <th>{t("tabloDetail.colName")}</th>
                <th>{t("tabloDetail.colType")}</th>
                <th>{t("tabloDetail.colPrimaryKey")}</th>
                <th>{t("tabloDetail.colTag")}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {draft.kolonlar.map((kolon: DraftKolon) => (
                <KolonRow
                  key={kolon.id}
                  kolon={kolon}
                  tags={tags}
                  onRename={onChangeKolonName}
                  onChangeTag={onChangeKolonTag}
                  onChangePrimaryKey={onChangeKolonPrimaryKey}
                  onToggleDelete={onToggleDeleteKolon}
                />
              ))}
              {draft.kolonlar.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty-hint">
                    {t("tabloDetail.emptyColumns")}
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          {canWrite && (
            <form className="add-kolon-form" onSubmit={handleAddKolonSubmit}>
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
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={kolonPrimaryKey}
                  onChange={(e) => setKolonPrimaryKey(e.target.checked)}
                />
                {t("tabloDetail.primaryKeyLabel")}
              </label>
              <button className="btn" type="submit">
                {t("tabloDetail.addColumn")}
              </button>
            </form>
          )}

          {canWrite && (
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
          )}
        </div>
      </fieldset>
    </section>
  );
}
