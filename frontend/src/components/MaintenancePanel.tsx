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
import { WorkspaceView } from "./WorkspaceNav";

const TARGET_TYPES: AuditTargetType[] = ["TABLE", "COLUMN", "SCHEMA", "TAG", "USER"];
const HEALTH_SERVICES = ["postgres", "redis", "minio", "backend"] as const;
const SUMMARY_FIELDS = ["schemaCount", "tableCount", "columnCount", "userCount"] as const;
type SummaryField = (typeof SUMMARY_FIELDS)[number];

/**
 * Sadece Şemalar/Kullanıcılar için — ikisinin de sol menüde tıklanınca gidilecek TEK bir hedef
 * görünümü var (WorkspaceNav "schemas"/"users"). Tablolar/Kolonlar icin boyle bir hedef yok
 * (sema agacinin icine gomulu, tek bir "listesi" sayfasi yok) — o kartlar bilerek tiklanamaz
 * kaliyor, tiklansaydi hangi tabloya/schema'ya gidecegi belirsiz olurdu.
 */
const SUMMARY_NAV_TARGETS: Partial<Record<SummaryField, WorkspaceView>> = {
  schemaCount: "schemas",
  userCount: "users",
};

/**
 * PrimeReact `TabView` "unstyled" modda (bkz. App.tsx'teki PrimeReactProvider) kendi p-tabview-*
 * class'larini basmiyor, ciplak bir <ul><li> listesi olarak render oluyor — projenin diger
 * bilesenleri (Button, DataTable) gibi kendi CSS'imizi yazan basit bir tab bar tercih edildi,
 * PrimeReact'in unstyled pt (passthrough) API'siyle ugrasmak yerine.
 */
const MAINTENANCE_TABS = ["entities", "serviceStatus", "auditLog", "backups"] as const;
type MaintenanceTab = (typeof MAINTENANCE_TABS)[number];

const TAB_LABEL_KEYS: Record<MaintenanceTab, string> = {
  entities: "tabEntities",
  serviceStatus: "tabServiceStatus",
  auditLog: "auditLogTitle",
  backups: "backupListTitle",
};

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
  onDownloadBackup: (key: string) => void;
  onNavigate: (view: WorkspaceView) => void;
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
  onDownloadBackup,
  onNavigate,
}: MaintenancePanelProps) {
  const { t, i18n } = useTranslation();
  const [activeTab, setActiveTab] = useState<MaintenanceTab>("entities");
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
      </div>

      <div className="tab-bar-card detail-card">
        <div className="tab-bar" role="tablist">
          {MAINTENANCE_TABS.map((tab) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={activeTab === tab}
              className={`tab-button ${activeTab === tab ? "tab-button-active" : ""}`}
              onClick={() => setActiveTab(tab)}
            >
              {t(`maintenance.${TAB_LABEL_KEYS[tab]}`)}
            </button>
          ))}
        </div>

        {activeTab === "entities" && (
          <div className="tab-panel">
            <div className="maintenance-summary-cards flex">
              {SUMMARY_FIELDS.map((field) => {
                const navTarget = SUMMARY_NAV_TARGETS[field];
                const cardBody = (
                  <>
                    <span className="summary-card-icon" aria-hidden="true">
                      {SUMMARY_ICONS[field]}
                    </span>
                    <div className="summary-card-body">
                      <span className="summary-card-value">{summary?.[field] ?? "-"}</span>
                      <span className="summary-card-label">{t(`maintenance.${field}`)}</span>
                    </div>
                  </>
                );
                return navTarget ? (
                  <button
                    key={field}
                    type="button"
                    className="summary-card summary-card-link"
                    onClick={() => onNavigate(navTarget)}
                  >
                    {cardBody}
                  </button>
                ) : (
                  <div key={field} className="summary-card">
                    {cardBody}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {activeTab === "serviceStatus" && (
          <div className="tab-panel">
            <div className="maintenance-summary-cards flex">
              {HEALTH_SERVICES.map((service) => {
                const up = health?.[service] ?? false;
                return (
                  <div key={service} className="summary-card">
                    <span
                      className={`summary-card-icon health-icon ${up ? "health-up" : "health-down"}`}
                      aria-hidden="true"
                    >
                      <span className="health-dot" />
                    </span>
                    <div className="summary-card-body">
                      <span className="summary-card-value">
                        {t(up ? "maintenance.serviceUp" : "maintenance.serviceDown")}
                      </span>
                      <span className="summary-card-label">
                        {t(`maintenance.service.${service}`)}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {activeTab === "auditLog" && (
          <div className="tab-panel">
            <div className="maintenance-audit-header flex align-items-center justify-content-between">
              <h3>{t("maintenance.auditLogTitle")}</h3>
              <Button
                className="btn btn-primary"
                label={t("maintenance.backup")}
                loading={backingUp}
                onClick={onBackup}
              />
            </div>

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
                  <span
                    className={`operation-badge operation-${operationCategory(row.operationType)}`}
                  >
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
        )}

        {activeTab === "backups" && (
          <div className="tab-panel">
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
                  <button
                    type="button"
                    className="mono backup-file-key backup-file-download"
                    title={t("maintenance.downloadBackup")}
                    onClick={() => onDownloadBackup(row.key)}
                  >
                    {row.key}
                  </button>
                )}
              />
            </DataTable>
          </div>
        )}
      </div>
    </section>
  );
}
