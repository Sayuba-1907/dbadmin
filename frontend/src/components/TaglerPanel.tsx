import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { KolonUsage, Tag } from "../api/tags";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../notifications/ConfirmProvider";

interface TaglerPanelProps {
  tags: Tag[];
  onLoadUsage: (tagId: number) => Promise<KolonUsage[]>;
  onRename: (tagId: number, name: string) => void;
  onDelete: (tagId: number) => void;
}

/**
 * "Tagler" gorunumu: sistemdeki tum etiketleri listeler, her birinin yaninda bir "Ayrıntı"
 * butonu vardir. Butona basilinca o etiketi tasiyan kolonlar (schema.tablo.kolon seklinde)
 * aciklanir — TabloSidebar'daki schema acilir/kapanir mantigiyla ayni: birden fazla tag'in
 * ayrintisi ayni anda acik kalabilir, accordion (tekli) degil.
 * <p>
 * Kullanim verisi tag basina lazy cekilir (sadece "Ayrıntı"ya basildiginda) ve bir kez
 * cekildikten sonra {@code usageByTagId} icinde onbelleklenir — ayni etiketi tekrar
 * ac/kapa yapmak ikinci bir istek atmaz.
 */
export function TaglerPanel({ tags, onLoadUsage, onRename, onDelete }: TaglerPanelProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const confirm = useConfirm();
  // TabloSidebar'daki schema acilir/kapanir deseniyle ayni (bkz. expandedSchemaIds): Set
  // oldugu icin birden fazla tag'in ayrinti alani AYNI ANDA acik kalabiliyor, tek elemanli
  // bir "su an acik olan" degeri degil.
  const [expandedTagIds, setExpandedTagIds] = useState<Set<number>>(new Set());
  const [usageByTagId, setUsageByTagId] = useState<Map<number, KolonUsage[]>>(new Map());
  const [loadingTagIds, setLoadingTagIds] = useState<Set<number>>(new Set());
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
    if (expandedTagIds.has(tagId)) {
      setExpandedTagIds((prev) => {
        const next = new Set(prev);
        next.delete(tagId);
        return next;
      });
      return;
    }
    setExpandedTagIds((prev) => new Set(prev).add(tagId));
    if (usageByTagId.has(tagId)) {
      return;
    }
    setLoadingTagIds((prev) => new Set(prev).add(tagId));
    try {
      const usage = await onLoadUsage(tagId);
      setUsageByTagId((prev) => new Map(prev).set(tagId, usage));
    } finally {
      setLoadingTagIds((prev) => {
        const next = new Set(prev);
        next.delete(tagId);
        return next;
      });
    }
  }

  return (
    <section className="tagler-panel fadeinup animation-duration-200">
      <h2>{t("tagler.title")}</h2>
      <ul className="tagler-list">
        {tags.map((tag) => {
          const expanded = expandedTagIds.has(tag.id);
          const usage = usageByTagId.get(tag.id);
          const loading = loadingTagIds.has(tag.id);
          const isEditing = editingTagId === tag.id;
          return (
            <li key={tag.id} className="tagler-item">
              <div className="tagler-item-row">
                {isEditing ? (
                  <form
                    className="inline-edit-form inline-flex"
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
                    {/* Detay artik ayri bir "Ayrıntı" butonuyla degil, isme (genel olarak
                        satirin bu alanina) tiklayinca aciliyor/kapaniyor — bkz. TabloSidebar'daki
                        schema baslik satiriyla ayni desen. */}
                    <span
                      className="tagler-name inline-block overflow-hidden text-overflow-ellipsis white-space-nowrap cursor-pointer"
                      role="button"
                      tabIndex={0}
                      onClick={() => handleToggleDetail(tag.id)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          handleToggleDetail(tag.id);
                        }
                      }}
                    >
                      {tag.name}
                    </span>
                    <div className="tagler-item-actions flex justify-content-end">
                      {canWrite && (
                        <button
                          className="btn btn-link"
                          onClick={() => {
                            setRenameDraft(tag.name);
                            setEditingTagId(tag.id);
                          }}
                        >
                          {t("common.edit")}
                        </button>
                      )}
                      {canWrite && (
                        <button
                          className="btn btn-link btn-danger"
                          onClick={async () => {
                            if (await confirm(t("tagler.confirmDelete", { name: tag.name }))) {
                              onDelete(tag.id);
                            }
                          }}
                        >
                          {t("common.delete")}
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
              {expanded && (
                <div className="tagler-usage">
                  {loading && (
                    <div role="status" aria-busy="true" aria-label={t("tagler.loadingUsage")}>
                      {[0, 1, 2].map((i) => (
                        <div
                          key={i}
                          className="skeleton skeleton-block"
                          style={{ height: 14, marginBottom: 8, width: `${70 - i * 12}%` }}
                        />
                      ))}
                    </div>
                  )}
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
