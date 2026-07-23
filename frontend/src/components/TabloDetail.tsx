import { FormEvent, useState } from "react";
import { CreateKolonInput, KOLON_TYPES, Tablo, KolonType } from "../api/tablolar";
import { Tag } from "../api/tags";
import { KolonRow } from "./KolonRow";

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
  const [kolonName, setKolonName] = useState("");
  const [kolonType, setKolonType] = useState<KolonType>(KOLON_TYPES[0]);
  const [submitting, setSubmitting] = useState(false);

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
              Kaydet
            </button>
            <button
              type="button"
              className="btn btn-link"
              onClick={() => {
                setTabloNameDraft(tablo.name);
                setEditingTabloName(false);
              }}
            >
              Vazgec
            </button>
          </form>
        ) : (
          <h2>
            {tablo.name}{" "}
            <button className="btn btn-link" onClick={() => setEditingTabloName(true)}>
              Duzenle
            </button>
          </h2>
        )}
        <button className="btn btn-danger" onClick={() => onDeleteTablo(tablo.id)}>
          Tabloyu Sil
        </button>
      </div>

      <table className="kolon-table">
        <thead>
          <tr>
            <th>Kolon</th>
            <th>Tip</th>
            <th>Tag</th>
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
                Henuz kolon yok
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <form className="add-kolon-form" onSubmit={handleAddKolon}>
        <input
          type="text"
          placeholder="kolon_adi"
          value={kolonName}
          onChange={(e) => setKolonName(e.target.value)}
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
          Kolon Ekle
        </button>
      </form>

      <form className="add-tag-form" onSubmit={handleCreateTagSubmit}>
        <input
          type="text"
          placeholder="yeni_tag_adi"
          value={newTagName}
          onChange={(e) => setNewTagName(e.target.value)}
          required
        />
        <button className="btn" type="submit">
          Tag Olustur
        </button>
      </form>
    </section>
  );
}
