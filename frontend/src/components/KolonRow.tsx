import { FormEvent, useState } from "react";
import { Kolon } from "../api/tablolar";
import { Tag } from "../api/tags";

interface KolonRowProps {
  kolon: Kolon;
  tags: Tag[];
  onRename: (kolonId: number, name: string) => Promise<void>;
  onChangeTag: (kolonId: number, tagId: number | null) => Promise<void>;
  onDelete: (kolonId: number) => void;
}

export function KolonRow({ kolon, tags, onRename, onChangeTag, onDelete }: KolonRowProps) {
  const [editing, setEditing] = useState(false);
  const [draftName, setDraftName] = useState(kolon.name);

  async function handleRenameSubmit(event: FormEvent) {
    event.preventDefault();
    await onRename(kolon.id, draftName);
    setEditing(false);
  }

  return (
    <tr>
      <td>
        {editing ? (
          <form className="inline-edit-form" onSubmit={handleRenameSubmit}>
            <input
              type="text"
              value={draftName}
              onChange={(e) => setDraftName(e.target.value)}
              autoFocus
            />
            <button type="submit" className="btn btn-link">
              Kaydet
            </button>
            <button
              type="button"
              className="btn btn-link"
              onClick={() => {
                setDraftName(kolon.name);
                setEditing(false);
              }}
            >
              Vazgec
            </button>
          </form>
        ) : (
          <>
            {kolon.name}{" "}
            <button className="btn btn-link" onClick={() => setEditing(true)}>
              Duzenle
            </button>
          </>
        )}
      </td>
      <td>
        <span className="type-badge">{kolon.type}</span>
      </td>
      <td>
        <select
          value={kolon.tagId ?? ""}
          onChange={(e) => onChangeTag(kolon.id, e.target.value ? Number(e.target.value) : null)}
        >
          <option value="">- tag yok -</option>
          {tags.map((tag) => (
            <option key={tag.id} value={tag.id}>
              {tag.name}
            </option>
          ))}
        </select>
      </td>
      <td>
        <button className="btn btn-link btn-danger" onClick={() => onDelete(kolon.id)}>
          Sil
        </button>
      </td>
    </tr>
  );
}
