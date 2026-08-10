import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Paginator, PaginatorPageChangeEvent } from "primereact/paginator";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import {
  AuditLog,
  AuditLogBackupListItem,
  AuditLogFilters,
  AuditOperationType,
  AuditTargetType,
} from "../api/auditLogs";
import { ServiceHealth, SystemSummary } from "../api/maintenance";
import { translateAuditDetail } from "../utils/translateAuditDetail";

const TARGET_TYPES: AuditTargetType[] = ["TABLE", "COLUMN", "SCHEMA", "TAG", "USER"];
const HEALTH_SERVICES = ["postgres", "redis", "tempo", "loki"] as const;

/** Aynı ikon kümesi WorkspaceNav'da da kullanılıyor (▦ Şemalar, ◉ Kullanıcılar) — burada tabloya/kolona genişletildi. */
const SUMMARY_ICONS = {
  schemaCount: "▦",
  tableCount: "⊞",
  columnCount: "▤",
  userCount: "◉",
} as const;

/**
 * İşlem tipinin kelime kökünden kategori çıkarır — "ne oldu" bilgisini tabloyu taramadan
 * renkle taşımak için (CREATED/ADDED yeşil, DELETED kırmızı, geri kalanı nötr). Backend'den
 * ayrı bir alan gelmiyor, sadece enum adının kendisinden türetiliyor.
 */
function operationCategory(op: AuditOperationType): "created" | "deleted" | "changed" {
  if (op.endsWith("CREATED") || op.endsWith("ADDED")) {
    return "created";
  }
  if (op.endsWith("DELETED")) {
    return "deleted";
  }
  return "changed";
}

interface MaintenancePanelProps {
  summary: SystemSummary | null;
  health: ServiceHealth | null;
  auditLogs: AuditLog[];
  auditLogsTotal: number;
  page: number;
  pageSize: number;
  loading: boolean;
  backingUp: boolean;
  backupList: AuditLogBackupListItem[];
  onFilterChange: (filters: AuditLogFilters) => void;
  onPageChange: (page: number) => void;
  onBackup: () => void;
}

/**
 * "Bakım" görünümü — sadece ADMIN'e açık (bkz. WorkspaceNav, SecurityConfig). Bkz.
 * requirement-maintenance-audit-backup.md: (A) sistem özeti + servis sağlığı kartları,
 * (B) mevcut {@code GET /api/audit-logs} ucuna bağlı filtrelenebilir tablo + "Yedekle" butonu,
 * (C) MinIO'daki geçmiş yedeklerin salt-okunur listesi.
 * <p>
 * Üç bölüm de {@code .detail-card} ile çerçeveleniyor (TableDetail'deki kart deseniyle aynı) —
 * sayfanın üç ayrı derdi (durum, canlı log, arşiv) görsel olarak da ayrışsın diye.
 * DB kimliği/insan adı ayrımı (DESIGN.md imzası) burada da geçerli: yedek dosya adı ve
 * hedef (TABLE#123 gibi) `mono`, kullanıcı adları `sans` kalıyor.
 * <p>
 * Filtre inputları burada kendi taslak state'ini tutar (her tuş vuruşunda istek atmamak için) —
 * sadece "Filtrele"ye basılınca {@code onFilterChange} çağrılır, TableDetail'deki
 * "taslak biriktir, Kaydet'e basınca gönder" deseniyle aynı fikir.
 */
export function MaintenancePanel({
  summary,
  health,
  auditLogs,
  auditLogsTotal,
  page,
  pageSize,
  loading,
  backingUp,
  backupList,
  onFilterChange,
  onPageChange,
  onBackup,
}: MaintenancePanelProps) {
  const { t, i18n } = useTranslation();
  const [userIdInput, setUserIdInput] = useState("");
  const [targetTypeInput, setTargetTypeInput] = useState<AuditTargetType | "">("");
  const [targetIdInput, setTargetIdInput] = useState("");
  const [fromInput, setFromInput] = useState("");
  const [toInput, setToInput] = useState("");

  function handleFilterSubmit(event: FormEvent) {
    event.preventDefault();
    onFilterChange({
      userId: userIdInput ? Number(userIdInput) : undefined,
      targetType: targetTypeInput || undefined,
      targetId: targetIdInput ? Number(targetIdInput) : undefined,
      // Tarih inputu gun cozunurlugunde (<input type="date">) — gunun basi/sonuna genisletiliyor
      // ki "from" o gunu disarida birakmasin, "to" o gunu tam kapsasin.
      from: fromInput ? `${fromInput}T00:00:00Z` : undefined,
      to: toInput ? `${toInput}T23:59:59Z` : undefined,
    });
  }

  function handleClearFilters() {
    setUserIdInput("");
    setTargetTypeInput("");
    setTargetIdInput("");
    setFromInput("");
    setToInput("");
    onFilterChange({});
  }

  return (
    <section className="maintenance-panel fadeinup animation-duration-200">
      <div className="maintenance-header flex align-items-center justify-content-between">
        <h2>{t("maintenance.title")}</h2>
        <Button
          className="btn btn-primary"
          label={t("maintenance.backup")}
          loading={backingUp}
          onClick={onBackup}
        />
      </div>

      <div className="maintenance-status-card detail-card">
        <div className="maintenance-summary-cards flex">
          {(["schemaCount", "tableCount", "columnCount", "userCount"] as const).map((field) => (
            <div key={field} className="summary-card">
              <span className="summary-card-icon" aria-hidden="true">
                {SUMMARY_ICONS[field]}
              </span>
              <div className="summary-card-body">
                <span className="summary-card-value">{summary?.[field] ?? "-"}</span>
                <span className="summary-card-label">{t(`maintenance.${field}`)}</span>
              </div>
            </div>
          ))}
        </div>

        <div className="maintenance-health flex">
          {HEALTH_SERVICES.map((service) => {
            const up = health?.[service] ?? false;
            return (
              <span key={service} className={`health-badge ${up ? "health-up" : "health-down"}`}>
                <span className="health-dot" aria-hidden="true" />
                {t(`maintenance.service.${service}`)}
              </span>
            );
          })}
        </div>
      </div>

      <div className="maintenance-audit-card detail-card">
        <h3>{t("maintenance.auditLogTitle")}</h3>

        <form
          className="maintenance-filters flex align-items-center flex-wrap"
          onSubmit={handleFilterSubmit}
        >
          <InputText
            type="number"
            placeholder={t("maintenance.filterUserId")}
            value={userIdInput}
            onChange={(e) => setUserIdInput(e.target.value)}
          />
          <select
            value={targetTypeInput}
            onChange={(e) => setTargetTypeInput(e.target.value as AuditTargetType | "")}
          >
            <option value="">{t("maintenance.filterAllTargetTypes")}</option>
            {TARGET_TYPES.map((tt) => (
              <option key={tt} value={tt}>
                {tt}
              </option>
            ))}
          </select>
          <InputText
            type="number"
            placeholder={t("maintenance.filterTargetId")}
            value={targetIdInput}
            onChange={(e) => setTargetIdInput(e.target.value)}
          />
          <input
            type="date"
            aria-label={t("maintenance.filterFrom")}
            value={fromInput}
            onChange={(e) => setFromInput(e.target.value)}
          />
          <input
            type="date"
            aria-label={t("maintenance.filterTo")}
            value={toInput}
            onChange={(e) => setToInput(e.target.value)}
          />
          <Button
            className="btn btn-secondary"
            type="submit"
            label={t("maintenance.applyFilters")}
          />
          <button type="button" className="btn btn-link" onClick={handleClearFilters}>
            {t("maintenance.clearFilters")}
          </button>
        </form>

        <DataTable
          // "Detail" hucresi (translateAuditDetail) dil disindaki bir kaynaga (i18n.language)
          // bagli — DataTable'in kendisi bunu bir prop olarak izlemedigi icin (satirlar sadece
          // `value` referansi degisince yeniden render ediliyor), dil degisince tabloyu
          // key ile yeniden monte ederek satirlarin taze cevirtilmesini garanti ediyoruz.
          key={i18n.language}
          value={auditLogs}
          dataKey="id"
          loading={loading}
          className="audit-log-table w-full"
          emptyMessage={t("maintenance.auditLogEmpty")}
        >
          <Column
            field="createdAt"
            header={t("maintenance.colCreatedAt")}
            body={(row: AuditLog) => new Date(row.createdAt).toLocaleString()}
          />
          <Column field="username" header={t("maintenance.colUsername")} />
          <Column
            field="operationType"
            header={t("maintenance.colOperationType")}
            body={(row: AuditLog) => (
              <span className={`operation-badge operation-${operationCategory(row.operationType)}`}>
                {row.operationType}
              </span>
            )}
          />
          <Column
            header={t("maintenance.colTarget")}
            body={(row: AuditLog) => (
              <span className="mono">{`${row.targetType}#${row.targetId}`}</span>
            )}
          />
          <Column
            field="detail"
            header={t("maintenance.colDetail")}
            body={(row: AuditLog) => translateAuditDetail(row.detail, i18n.language)}
          />
        </DataTable>

        <Paginator
          first={page * pageSize}
          rows={pageSize}
          totalRecords={auditLogsTotal}
          onPageChange={(e: PaginatorPageChangeEvent) => onPageChange(e.page)}
        />
      </div>

      <div className="maintenance-backups-card detail-card">
        <h3>{t("maintenance.backupListTitle")}</h3>
        <DataTable
          value={backupList}
          dataKey="key"
          className="backup-list-table w-full"
          emptyMessage={t("maintenance.backupListEmpty")}
        >
          <Column
            field="backedUpAt"
            header={t("maintenance.colBackedUpAt")}
            body={(row: AuditLogBackupListItem) => new Date(row.backedUpAt).toLocaleString()}
          />
          <Column field="backedUpBy" header={t("maintenance.colBackedUpBy")} />
          <Column
            field="rowCount"
            header={t("maintenance.colRowCount")}
            body={(row: AuditLogBackupListItem) => (
              <span className="backup-row-count">{row.rowCount}</span>
            )}
          />
          <Column
            field="key"
            header={t("maintenance.colBackupFile")}
            body={(row: AuditLogBackupListItem) => (
              <span className="mono backup-file-key">{row.key}</span>
            )}
          />
        </DataTable>
      </div>
    </section>
  );
}
