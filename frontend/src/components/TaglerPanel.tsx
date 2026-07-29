import { useState } from "react";
import { useTranslation } from "react-i18next";
import { KolonUsage, Tag } from "../api/tags";

interface TaglerPanelProps {
  tags: Tag[];
  onLoadUsage: (tagId: number) => Promise<KolonUsage[]>;
}

/**
 * "Tagler" gorunumu: sistemdeki tum etiketleri listeler, her birinin yaninda bir "Ayrıntı"
 * butonu vardir. Butona basilinca o etiketi tasiyan kolonlar (schema.tablo.kolon seklinde)
 * aciklanir — TabloSidebar'daki schema acilir/kapanir (accordion) deseniyle ayni.
 * <p>
 * Kullanim verisi tag basina lazy cekilir (sadece "Ayrıntı"ya basildiginda) ve bir kez
 * cekildikten sonra {@code usageByTagId} icinde onbelleklenir — ayni etiketi tekrar
 * ac/kapa yapmak ikinci bir istek atmaz.
 */
export function TaglerPanel({ tags, onLoadUsage }: TaglerPanelProps) {
  const { t } = useTranslation();
  const [expandedTagId, setExpandedTagId] = useState<number | null>(null);
  const [usageByTagId, setUsageByTagId] = useState<Map<number, KolonUsage[]>>(new Map());
  const [loadingTagId, setLoadingTagId] = useState<number | null>(null);

  async function handleToggleDetail(tagId: number) {
    if (expandedTagId === tagId) {
      setExpandedTagId(null);
      return;
    }
    setExpandedTagId(tagId);
    if (usageByTagId.has(tagId)) {
      return;
    }
    setLoadingTagId(tagId);
    try {
      const usage = await onLoadUsage(tagId);
      setUsageByTagId((prev) => new Map(prev).set(tagId, usage));
    } finally {
      setLoadingTagId(null);
    }
  }

  return (
    <section className="tagler-panel">
      <h2>{t("tagler.title")}</h2>
      <ul className="tagler-list">
        {tags.map((tag) => {
          const expanded = expandedTagId === tag.id;
          const usage = usageByTagId.get(tag.id);
          const loading = loadingTagId === tag.id;
          return (
            <li key={tag.id} className="tagler-item">
              <div className="tagler-item-row">
                <span className="tagler-name">{tag.name}</span>
                <button className="btn btn-link" onClick={() => handleToggleDetail(tag.id)}>
                  {expanded ? t("tagler.hideDetail") : t("tagler.showDetail")}
                </button>
              </div>
              {expanded && (
                <div className="tagler-usage">
                  {loading && <p className="loading-hint">{t("tagler.loadingUsage")}</p>}
                  {!loading && usage && usage.length === 0 && (
                    <p className="empty-hint">{t("tagler.noUsage")}</p>
                  )}
                  {!loading && usage && usage.length > 0 && (
                    <ul className="tagler-usage-list">
                      {usage.map((u) => (
                        <li key={u.kolonId}>
                          {t("tagler.usageItem", {
                            schema: u.schemaName,
                            table: u.tabloName,
                            column: u.kolonName,
                          })}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </li>
          );
        })}
        {tags.length === 0 && <li className="empty-hint">{t("tagler.empty")}</li>}
      </ul>
    </section>
  );
}
