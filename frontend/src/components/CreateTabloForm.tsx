import { FormEvent, useState } from "react";
import { CreateKolonInput, KOLON_TYPES, KolonType } from "../api/tablolar";

interface CreateTabloFormProps {
  onSubmit: (name: string, kolonlar: CreateKolonInput[]) => Promise<void>;
  onClose: () => void;
}

interface DraftKolon {
  name: string;
  type: KolonType;
}

export function CreateTabloForm({ onSubmit, onClose }: CreateTabloFormProps) {
  const [name, setName] = useState("");
  const [kolonlar, setKolonlar] = useState<DraftKolon[]>([{ name: "", type: KOLON_TYPES[0] }]);
  const [submitting, setSubmitting] = useState(false);

  function updateKolon(index: number, patch: Partial<DraftKolon>) {
    setKolonlar((prev) => prev.map((k, i) => (i === index ? { ...k, ...patch } : k)));
  }

  function addKolonRow() {
    setKolonlar((prev) => [...prev, { name: "", type: KOLON_TYPES[0] }]);
  }

  function removeKolonRow(index: number) {
    setKolonlar((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const validKolonlar: CreateKolonInput[] = kolonlar
        .filter((k) => k.name.trim() !== "")
        .map((k) => ({ name: k.name, type: k.type }));
      await onSubmit(name, validKolonlar);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay">
      <form className="modal create-tablo-form" onSubmit={handleSubmit}>
        <h2>Yeni Tablo</h2>

        <label>
          Tablo adi
          <input
            type="text"
            placeholder="tablo_adi"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </label>

        <div className="kolon-rows">
          {kolonlar.map((kolon, index) => (
            <div className="kolon-row" key={index}>
              <input
                type="text"
                placeholder="kolon_adi"
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
              <button
                type="button"
                className="btn btn-link btn-danger"
                onClick={() => removeKolonRow(index)}
              >
                Sil
              </button>
            </div>
          ))}
        </div>

        <button type="button" className="btn btn-link" onClick={addKolonRow}>
          + Kolon ekle
        </button>

        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose}>
            Vazgec
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            Olustur
          </button>
        </div>
      </form>
    </div>
  );
}
