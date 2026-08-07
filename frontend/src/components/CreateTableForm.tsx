import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { InputText } from "primereact/inputtext";
import { Button } from "primereact/button";
import { Schema } from "../api/schemas";
import { CreateColumnInput, COLUMN_TYPES, ColumnType } from "../api/tables";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";

interface CreateTableFormProps {
  schemas: Schema[];
  onSubmit: (name: string, columns: CreateColumnInput[], schemaId: number) => Promise<void>;
  onClose: () => void;
}

/** Formdaki henuz gonderilmemis kolon taslagi — CreateColumnInput'tan farkli, cunku bos satirlar da (name="") burada gecici olarak tutulabilir. */
interface DraftColumn {
  name: string;
  type: ColumnType;
  primaryKey: boolean;
}

/** "Yeni Table" modal formu: tablo adi + hangi schema'ya kurulacagi + dinamik sayida kolon satiri (ekle/sil). */
export function CreateTableForm({ schemas, onSubmit, onClose }: CreateTableFormProps) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [schemaId, setSchemaId] = useState<number | "">(schemas[0]?.id ?? "");
  const [columns, setKolonlar] = useState<DraftColumn[]>([
    { name: "", type: COLUMN_TYPES[0], primaryKey: false },
  ]);
  const [submitting, setSubmitting] = useState(false);

  /** Listedeki tek bir kolon satirini gunceller — React'ta array state'i dogrudan mutate etmek yerine hep yeni bir array olusturulur (immutability). */
  function updateKolon(index: number, patch: Partial<DraftColumn>) {
    setKolonlar((prev) => prev.map((k, i) => (i === index ? { ...k, ...patch } : k)));
  }

  function addKolonRow() {
    setKolonlar((prev) => [...prev, { name: "", type: COLUMN_TYPES[0], primaryKey: false }]);
  }

  function removeKolonRow(index: number) {
    setKolonlar((prev) => prev.filter((_, i) => i !== index));
  }

  /** Bos birakilmis (isim girilmemis) kolon satirlarini eleyip sadece dolu olanlari backend'e gonderir. */
  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (schemaId === "") {
      return;
    }
    setSubmitting(true);
    try {
      const validKolonlar: CreateColumnInput[] = columns
        .filter((k) => k.name.trim() !== "")
        .map((k) => ({ name: k.name, type: k.type, primaryKey: k.primaryKey }));
      await onSubmit(name, validKolonlar, schemaId);
    } finally {
      setSubmitting(false);
    }
  }

  // Her tablo bir schema'nin icine kurulur ve "public" artik secilebilir bir hedef degil
  // (altyapiya ait, backend onu listelemiyor). Dolayisiyla hic schema yokken gosterilecek bir
  // secenek de yok: bos bir dropdown'la calismayan form yerine ne yapmasi gerektigini soyluyoruz.
  if (schemas.length === 0) {
    return (
      <div className="modal-overlay fixed flex align-items-center justify-content-center">
        <div className="modal create-table-form">
          <h2>{t("createTabloForm.title")}</h2>
          <p>{t("createTabloForm.noSchemaHint")}</p>
          <div className="modal-actions flex justify-content-end">
            <Button type="button" className="btn" onClick={onClose} label={t("common.cancel")} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay fixed flex align-items-center justify-content-center">
      <form className="modal create-table-form" onSubmit={handleSubmit}>
        <h2>{t("createTabloForm.title")}</h2>

        <label>
          {t("createTabloForm.tableNameLabel")}
          <InputText
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

        <label>
          {t("createTabloForm.schemaLabel")}
          <select value={schemaId} onChange={(e) => setSchemaId(Number(e.target.value))} required>
            {schemas.map((schema) => (
              <option key={schema.id} value={schema.id}>
                {schema.name}
              </option>
            ))}
          </select>
        </label>

        <div className="column-rows">
          {columns.map((column, index) => (
            <div className="column-row" key={index}>
              <InputText
                type="text"
                placeholder={t("tabloDetail.columnNamePlaceholder")}
                value={column.name}
                onChange={(e) => updateKolon(index, { name: e.target.value })}
              />
              <select
                value={column.type}
                onChange={(e) => updateKolon(index, { type: e.target.value as ColumnType })}
              >
                {COLUMN_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={column.primaryKey}
                  onChange={(e) => updateKolon(index, { primaryKey: e.target.checked })}
                />
                {t("tabloDetail.primaryKeyLabel")}
              </label>
              <Button
                type="button"
                className="btn btn-link btn-danger"
                onClick={() => removeKolonRow(index)}
                label={t("common.delete")}
              />
            </div>
          ))}
        </div>

        <Button
          type="button"
          className="btn btn-link"
          onClick={addKolonRow}
          label={t("createTabloForm.addColumnRow")}
        />

        <div className="modal-actions">
          <Button type="button" className="btn" onClick={onClose} label={t("common.cancel")} />
          <Button
            type="submit"
            className="btn btn-primary"
            disabled={submitting}
            label={t("createTabloForm.submit")}
          />
        </div>
      </form>
    </div>
  );
}
