import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { KolonUsage, Tag } from "../api/tags";
import { useAuth } from "../auth/AuthProvider";

interface TaglerPanelProps {
  tags: Tag[];
  onLoadUsage: (tagId: number) => Promise<KolonUsage[]>;
  onRename: (tagId: number, name: string) => void;
  onDelete: (tagId: number) => void;
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
export function TaglerPanel({ tags, onLoadUsage, onRename, onDelete }: TaglerPanelProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const [expandedTagId, setExpandedTagId] = useState<number | null>(null);
  const [usageByTagId, setUsageByTagId] = useState<Map<number, KolonUsage[]>>(new Map());
  const [loadingTagId, setLoadingTagId] = useState<number | null>(null);
  // Schema/tablo yeniden adlandirmasindaki ile ayni desen: hangi tag su an duzenleme modunda,
  // ve o an input'ta yazan taslak isim — salt gorsel/gecici UI durumu.
  const [editingTagId, setEditingTagId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");

  function handleRenameSubmit(event: FormEvent, tagId: number) {
    event.preventDefault();
    onRename(tagId, renameDraft);
    setEditingTagId(null);
  }

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
          const isEditing = editingTagId === tag.id;
          return (
            <li key={tag.id} className="tagler-item">
              <div className="tagler-item-row">
                {isEditing ? (
                  <form
                    className="inline-edit-form"
                    onSubmit={(e) => handleRenameSubmit(e, tag.id)}
                  >
                    <input
                      type="text"
                      value={renameDraft}
                      onChange={(e) => setRenameDraft(e.target.value)}
                      autoFocus
                    />
                    <button type="submit" className="btn btn-link">
                      {t("common.save")}
                    </button>
                    <button
                      type="button"
                      className="btn btn-link"
                      onClick={() => setEditingTagId(null)}
                    >
                      {t("common.cancel")}
                    </button>
                  </form>
                ) : (
                  <>
                    <span className="tagler-name">{tag.name}</span>
                    <button className="btn btn-link" onClick={() => handleToggleDetail(tag.id)}>
                      {expanded ? t("tagler.hideDetail") : t("tagler.showDetail")}
                    </button>
                    <button
                      className="btn btn-link"
                      disabled={!canWrite}
                      onClick={() => {
                        setRenameDraft(tag.name);
                        setEditingTagId(tag.id);
                      }}
                    >
                      {t("common.edit")}
                    </button>
                    <button
                      className="btn btn-link btn-danger"
                      disabled={!canWrite}
                      onClick={() => {
                        if (window.confirm(t("tagler.confirmDelete", { name: tag.name }))) {
                          onDelete(tag.id);
                        }
                      }}
                    >
                      {t("common.delete")}
                    </button>
                  </>
                )}
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
