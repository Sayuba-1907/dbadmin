import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Kolon } from "../api/tablolar";
import { Tag } from "../api/tags";

interface KolonRowProps {
  kolon: Kolon;
  tags: Tag[];
  onRename: (kolonId: number, name: string) => Promise<void>;
  onChangeTag: (kolonId: number, tagId: number | null) => Promise<void>;
  onDelete: (kolonId: number) => void;
}

/**
 * Kolon tablosunda tek bir satir: isim (duzenlenebilir), tip (sabit — duzenlenemez, cunku
 * backend'de {@code updatable = false}), tag secici (dropdown) ve sil butonu.
 */
export function KolonRow({ kolon, tags, onRename, onChangeTag, onDelete }: KolonRowProps) {
  const { t } = useTranslation();
  // editing: bu satir su an "isim duzenleme" modunda mi. draftName: input'taki henuz kaydedilmemis metin.
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
              {t("common.save")}
            </button>
            <button
              type="button"
              className="btn btn-link"
              onClick={() => {
                setDraftName(kolon.name);
                setEditing(false);
              }}
            >
              {t("common.cancel")}
            </button>
          </form>
        ) : (
          <>
            {kolon.name}{" "}
            <button className="btn btn-link" onClick={() => setEditing(true)}>
              {t("common.edit")}
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
          <option value="">{t("kolonRow.noTag")}</option>
          {tags.map((tag) => (
            <option key={tag.id} value={tag.id}>
              {tag.name}
            </option>
          ))}
        </select>
      </td>
      <td>
        <button
          className="btn btn-link btn-danger"
          onClick={() => {
            if (window.confirm(t("kolonRow.confirmDelete", { name: kolon.name }))) {
              onDelete(kolon.id);
            }
          }}
        >
          {t("common.delete")}
        </button>
      </td>
    </tr>
  );
}
