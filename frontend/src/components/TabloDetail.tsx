import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Button } from "primereact/button";
import { Checkbox } from "primereact/checkbox";
import { DraftKolon, KOLON_TYPES, KolonType, TabloDraft } from "../api/tablolar";
import { Tag } from "../api/tags";
import { tagColorStyle } from "../utils/tagColor";
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
  // Kolon tablosunda ad'a gore arama — DataTable'in globalFilter'i, onceden hic yoktu (deneme:
  // PrimeReact DataTable, bkz. DECISIONS.md).
  const [kolonSearch, setKolonSearch] = useState("");

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
    <section className="detail-panel fadeinup animation-duration-200">
      {/* VIEWER rolu icin tum yazma kontrollerini tek yerden kapatir — fieldset'in disabled'i
          DOM derinligi fark etmeksizin her input/select/button torununa uygulanir, yani
          DataTable'in kolon body'lerindeki tekil kontrollere ayrica dokunmaya gerek yok. Backend zaten
          403 donuyor (bkz. SecurityConfig); bu sadece kullanicinin yapamayacagi bir seyi
          denemesini engelleyen bir UX katmani. */}
      <fieldset className="unstyled-fieldset" disabled={!canWrite}>
        <div className="detail-header flex align-items-center">
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
          <div className="detail-header-actions flex ml-auto">
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
          {draft.kolonlar.length > 0 && (
            <input
              type="text"
              className="sidebar-search block"
              style={{ marginBottom: "var(--space-2)", maxWidth: 240 }}
              placeholder={t("tabloDetail.columnSearchPlaceholder")}
              value={kolonSearch}
              onChange={(e) => setKolonSearch(e.target.value)}
            />
          )}
          <DataTable
            value={draft.kolonlar}
            dataKey="id"
            className="kolon-table w-full"
            emptyMessage={t("tabloDetail.emptyColumns")}
            globalFilter={kolonSearch}
            globalFilterFields={["name"]}
            rowClassName={(kolon: DraftKolon) =>
              kolon.silinecek ? "kolon-row-silinecek" : undefined
            }
          >
            <Column
              field="name"
              header={t("tabloDetail.colName")}
              sortable
              body={(kolon: DraftKolon) => (
                <>
                  <input
                    type="text"
                    className="mono"
                    value={kolon.name}
                    onChange={(e) => onChangeKolonName(kolon.id, e.target.value)}
                    disabled={kolon.silinecek}
                  />
                  {kolon.isNew && (
                    <span className="new-badge">{t("tabloDetail.newColumnBadge")}</span>
                  )}
                </>
              )}
            />
            <Column
              field="type"
              header={t("tabloDetail.colType")}
              sortable
              body={(kolon: DraftKolon) => (
                <span className={`type-badge type-badge-${kolon.type}`}>{kolon.type}</span>
              )}
            />
            <Column
              header={t("tabloDetail.colPrimaryKey")}
              body={(kolon: DraftKolon) => (
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={kolon.primaryKey}
                    disabled={kolon.silinecek}
                    onChange={(e) => onChangeKolonPrimaryKey(kolon.id, e.target.checked)}
                    aria-label={t("tabloDetail.primaryKeyLabel")}
                  />
                  {kolon.primaryKey && <span className="pk-badge">PK</span>}
                </label>
              )}
            />
            <Column
              header={t("tabloDetail.colTag")}
              body={(kolon: DraftKolon) => {
                const selectedTag = tags.find((tag) => tag.id === kolon.tagId);
                return (
                  <select
                    className="tag-select cursor-pointer"
                    style={selectedTag ? tagColorStyle(selectedTag.name) : undefined}
                    value={kolon.tagId ?? ""}
                    disabled={kolon.silinecek}
                    onChange={(e) =>
                      onChangeKolonTag(kolon.id, e.target.value ? Number(e.target.value) : null)
                    }
                  >
                    <option value="">{t("kolonRow.noTag")}</option>
                    {tags.map((tag) => (
                      <option key={tag.id} value={tag.id} style={tagColorStyle(tag.name)}>
                        {tag.name}
                      </option>
                    ))}
                  </select>
                );
              }}
            />
            <Column
              body={(kolon: DraftKolon) =>
                canWrite && (
                  <button
                    className="btn btn-link btn-danger"
                    onClick={() => onToggleDeleteKolon(kolon.id)}
                  >
                    {kolon.silinecek ? t("common.undo") : t("common.delete")}
                  </button>
                )
              }
            />
          </DataTable>

          {canWrite && (
            <form
              className="add-kolon-form flex align-items-center"
              onSubmit={handleAddKolonSubmit}
            >
              <InputText
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
                <Checkbox
                  checked={kolonPrimaryKey}
                  onChange={(e) => setKolonPrimaryKey(e.checked ?? false)}
                />
                {t("tabloDetail.primaryKeyLabel")}
              </label>
              <Button className="btn" type="submit" label={t("tabloDetail.addColumn")} />
            </form>
          )}

          {canWrite && (
            <form className="add-tag-form flex align-items-center" onSubmit={handleCreateTagSubmit}>
              <InputText
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
              <Button className="btn" type="submit" label={t("tabloDetail.createTag")} />
            </form>
          )}
        </div>
      </fieldset>
    </section>
  );
}
