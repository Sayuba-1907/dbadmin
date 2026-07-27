import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { CreateKolonInput, KOLON_TYPES, KolonType } from "../api/tablolar";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";

interface CreateTabloFormProps {
  onSubmit: (name: string, kolonlar: CreateKolonInput[]) => Promise<void>;
  onClose: () => void;
}

/** Formdaki henuz gonderilmemis kolon taslagi — CreateKolonInput'tan farkli, cunku bos satirlar da (name="") burada gecici olarak tutulabilir. */
interface DraftKolon {
  name: string;
  type: KolonType;
  primaryKey: boolean;
}

/** "Yeni Tablo" modal formu: tablo adi + dinamik sayida kolon satiri (ekle/sil). */
export function CreateTabloForm({ onSubmit, onClose }: CreateTabloFormProps) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [kolonlar, setKolonlar] = useState<DraftKolon[]>([
    { name: "", type: KOLON_TYPES[0], primaryKey: false },
  ]);
  const [submitting, setSubmitting] = useState(false);

  /** Listedeki tek bir kolon satirini gunceller — React'ta array state'i dogrudan mutate etmek yerine hep yeni bir array olusturulur (immutability). */
  function updateKolon(index: number, patch: Partial<DraftKolon>) {
    setKolonlar((prev) => prev.map((k, i) => (i === index ? { ...k, ...patch } : k)));
  }

  function addKolonRow() {
    setKolonlar((prev) => [...prev, { name: "", type: KOLON_TYPES[0], primaryKey: false }]);
  }

  function removeKolonRow(index: number) {
    setKolonlar((prev) => prev.filter((_, i) => i !== index));
  }

  /** Bos birakilmis (isim girilmemis) kolon satirlarini eleyip sadece dolu olanlari backend'e gonderir. */
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const validKolonlar: CreateKolonInput[] = kolonlar
        .filter((k) => k.name.trim() !== "")
        .map((k) => ({ name: k.name, type: k.type, primaryKey: k.primaryKey }));
      await onSubmit(name, validKolonlar);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay">
      <form className="modal create-tablo-form" onSubmit={handleSubmit}>
        <h2>{t("createTabloForm.title")}</h2>

        <label>
          {t("createTabloForm.tableNameLabel")}
          <input
            type="text"
            placeholder={t("createTabloForm.tableNamePlaceholder")}
            value={name}
            onChange={(e) => {
              clearCustomValidity(e);
              setName(e.target.value);
            }}
            onInvalid={onRequiredInvalid(t)}
            required
          />
        </label>

        <div className="kolon-rows">
          {kolonlar.map((kolon, index) => (
            <div className="kolon-row" key={index}>
              <input
                type="text"
                placeholder={t("tabloDetail.columnNamePlaceholder")}
                value={kolon.name}
                onChange={(e) => updateKolon(index, { name: e.target.value })}
              />
              <select
                value={kolon.type}
                onChange={(e) => updateKolon(index, { type: e.target.value as KolonType })}
              >
                {KOLON_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={kolon.primaryKey}
                  onChange={(e) => updateKolon(index, { primaryKey: e.target.checked })}
                />
                {t("tabloDetail.primaryKeyLabel")}
              </label>
              <button
                type="button"
                className="btn btn-link btn-danger"
                onClick={() => removeKolonRow(index)}
              >
                {t("common.delete")}
              </button>
            </div>
          ))}
        </div>

        <button type="button" className="btn btn-link" onClick={addKolonRow}>
          {t("createTabloForm.addColumnRow")}
        </button>

        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            {t("common.cancel")}
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {t("createTabloForm.submit")}
          </button>
        </div>
      </form>
    </div>
  );
}
