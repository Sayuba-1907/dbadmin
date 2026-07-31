import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";

interface CreateSchemaFormProps {
  onSubmit: (name: string) => Promise<void>;
  onClose: () => void;
}

/** "Yeni Schema" modal formu — CreateTabloForm ile ayni yapi, sadece tek bir isim alani var. */
export function CreateSchemaForm({ onSubmit, onClose }: CreateSchemaFormProps) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit(name);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay">
      <form className="modal create-schema-form" onSubmit={handleSubmit}>
        <h2>{t("createSchemaForm.title")}</h2>

        <label>
          {t("createSchemaForm.schemaNameLabel")}
          <input
            type="text"
            placeholder={t("createSchemaForm.schemaNamePlaceholder")}
            value={name}
            onChange={(e) => {
              clearCustomValidity(e);
              setName(e.target.value);
            }}
            onInvalid={onRequiredInvalid(t)}
            required
          />
        </label>

        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            {t("common.cancel")}
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {t("createSchemaForm.submit")}
          </button>
        </div>
      </form>
    </div>
  );
}
