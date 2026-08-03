import { useTranslation } from "react-i18next";
import { DraftKolon } from "../api/tablolar";
import { Tag } from "../api/tags";

interface KolonRowProps {
  kolon: DraftKolon;
  tags: Tag[];
  onRename: (kolonId: number, name: string) => void;
  onChangeTag: (kolonId: number, tagId: number | null) => void;
  onChangePrimaryKey: (kolonId: number, primaryKey: boolean) => void;
  onToggleDelete: (kolonId: number) => void;
}

/**
 * Kolon tablosunda tek bir satir: isim (her zaman duzenlenebilir input — TabloDetail'in genel
 * Kaydet/Vazgeç'i oldugu icin bu satirin kendi mini kaydet/iptal'ine gerek yok), tip (sabit —
 * duzenlenemez, backend'de {@code updatable = false}), birincil anahtar kutusu, tag secici
 * (dropdown) ve sil/geri-al butonu.
 * <p>
 * Butun onChange* callback'leri SENKRON: hicbiri API'ye gitmez, hepsi Dashboard'daki taslagi
 * gunceller. Bu yuzden eskiden PK kutusunu istek surerken kilitleyen {@code saving} state'i
 * artik gerekmiyor (kaldirildi) — degisiklik aninda yerel, network round-trip yok.
 */
export function KolonRow({
  kolon,
  tags,
  onRename,
  onChangeTag,
  onChangePrimaryKey,
  onToggleDelete,
}: KolonRowProps) {
  const { t } = useTranslation();

  return (
    <tr className={kolon.silinecek ? "kolon-row-silinecek" : undefined}>
      <td>
        <input
          type="text"
          className="mono"
          value={kolon.name}
          onChange={(e) => onRename(kolon.id, e.target.value)}
          disabled={kolon.silinecek}
        />
        {kolon.isNew && <span className="new-badge">{t("tabloDetail.newColumnBadge")}</span>}
      </td>
      <td>
        <span className={`type-badge type-badge-${kolon.type}`}>{kolon.type}</span>
      </td>
      <td>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={kolon.primaryKey}
            disabled={kolon.silinecek}
            onChange={(e) => onChangePrimaryKey(kolon.id, e.target.checked)}
            aria-label={t("tabloDetail.primaryKeyLabel")}
          />
          {kolon.primaryKey && <span className="pk-badge">PK</span>}
        </label>
      </td>
      <td>
        <select
          value={kolon.tagId ?? ""}
          disabled={kolon.silinecek}
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
        <button className="btn btn-link btn-danger" onClick={() => onToggleDelete(kolon.id)}>
          {kolon.silinecek ? t("common.undo") : t("common.delete")}
        </button>
      </td>
    </tr>
  );
}
