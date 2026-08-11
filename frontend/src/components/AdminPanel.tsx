import { useTranslation } from "react-i18next";
import { MaintenancePanel } from "./MaintenancePanel";
import { AuditLog, AuditLogBackupListItem, AuditLogFilters } from "../api/auditLogs";
import { ServiceHealth, SystemSummary } from "../api/maintenance";
import { WorkspaceView } from "./WorkspaceNav";

interface AdminPanelProps {
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
 * Requirement notu 9 ("Ayarlar Sayfası") — hesap popup'undaki "Ayarlar" flyout'unun "Yönetim"
 * secenegi (bkz. WorkspaceNav). Faydali linkler ARTIK burada DEGIL, kendi ayri sayfasinda (bkz.
 * UsefulLinksPanel) — flyout ucu bilerek: Oturumlar / Yönetim / Faydalı Linkler. Sadece ADMIN'e
 * acik (WorkspaceNav flyout'ta isAdmin degilse hic gosterilmiyor). Maintenance'in kendi
 * verisi/handler'lari hala Dashboard'daki useMaintenance hook'undan geliyor — burasi sadece
 * onlari MaintenancePanel'e tasiyan ince bir katman.
 */
export function AdminPanel(props: AdminPanelProps) {
  const { t } = useTranslation();

  return (
    <section className="settings-panel fadeinup animation-duration-200">
      <div className="settings-header flex align-items-center justify-content-between">
        <h2>{t("settings.tabAdmin")}</h2>
      </div>

      <div className="detail-card settings-admin-card">
        <MaintenancePanel
          summary={props.summary}
          health={props.health}
          auditLogs={props.auditLogs}
          auditLogsTotal={props.auditLogsTotal}
          page={props.page}
          pageSize={props.pageSize}
          loading={props.loading}
          backingUp={props.backingUp}
          backupList={props.backupList}
          onFilterChange={props.onFilterChange}
          onPageChange={props.onPageChange}
          onBackup={props.onBackup}
          onDownloadBackup={props.onDownloadBackup}
          onNavigate={props.onNavigate}
        />
      </div>
    </section>
  );
}
