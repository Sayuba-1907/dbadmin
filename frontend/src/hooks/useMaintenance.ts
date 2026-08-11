import { useCallback, useState } from "react";
import {
  AUDIT_LOG_PAGE_SIZE,
  AuditLog,
  AuditLogBackupListItem,
  AuditLogBackupResult,
  AuditLogFilters,
  backupAuditLogs,
  downloadBackupFile,
  getAuditLogs,
  getBackupList,
} from "../api/auditLogs";
import {
  ServiceHealth,
  SystemSummary,
  getServiceHealth,
  getSystemSummary,
} from "../api/maintenance";

/**
 * Maintenance sayfasinin (bkz. requirement-maintenance-audit-backup.md) tum okuma+yazma
 * sorumlulugu — useUsers/useTags'le ayni kalip (otomatik mount-cekme YOK, sadece sekme acilinca
 * {@link refresh} cagirilir). Hata yonetimi: bu hook hatayi YUTMAZ, firlatir — Dashboard
 * notifyFromError ile kullaniciya gosterir.
 */
export function useMaintenance() {
  const [summary, setSummary] = useState<SystemSummary | null>(null);
  const [health, setHealth] = useState<ServiceHealth | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [auditLogsTotal, setAuditLogsTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [loading, setLoading] = useState(false);
  const [backingUp, setBackingUp] = useState(false);
  const [backupList, setBackupList] = useState<AuditLogBackupListItem[]>([]);

  const loadAuditLogs = useCallback(async (targetPage: number, targetFilters: AuditLogFilters) => {
    const result = await getAuditLogs(targetFilters, targetPage);
    setAuditLogs(result.content);
    setAuditLogsTotal(result.totalElements);
  }, []);

  /** Sekmeye her girildiginde cagirilir (bkz. Dashboard#handleChangeActiveView) — filtre/sayfayi sifirlar, en guncel veriyi ceker. */
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [summaryResult, healthResult, backupListResult] = await Promise.all([
        getSystemSummary(),
        getServiceHealth(),
        getBackupList(),
      ]);
      setSummary(summaryResult);
      setHealth(healthResult);
      setBackupList(backupListResult);
      setFilters({});
      setPage(0);
      await loadAuditLogs(0, {});
    } finally {
      setLoading(false);
    }
  }, [loadAuditLogs]);

  const changeFilters = useCallback(
    async (newFilters: AuditLogFilters) => {
      setFilters(newFilters);
      setPage(0);
      setLoading(true);
      try {
        await loadAuditLogs(0, newFilters);
      } finally {
        setLoading(false);
      }
    },
    [loadAuditLogs]
  );

  const changePage = useCallback(
    async (newPage: number) => {
      setPage(newPage);
      setLoading(true);
      try {
        await loadAuditLogs(newPage, filters);
      } finally {
        setLoading(false);
      }
    },
    [loadAuditLogs, filters]
  );

  /** Backup basariliysa backend tabloyu temizler (Req-2.5) — listeyi ilk sayfaya/filtresiz donduruyoruz ki bosaldigi hemen gorunsun. */
  const backup = useCallback(async (): Promise<AuditLogBackupResult> => {
    setBackingUp(true);
    try {
      const result = await backupAuditLogs();
      setFilters({});
      setPage(0);
      await loadAuditLogs(0, {});
      setBackupList(await getBackupList());
      return result;
    } finally {
      setBackingUp(false);
    }
  }, [loadAuditLogs]);

  return {
    summary,
    health,
    auditLogs,
    auditLogsTotal,
    page,
    pageSize: AUDIT_LOG_PAGE_SIZE,
    filters,
    loading,
    backingUp,
    backupList,
    refresh,
    changeFilters,
    changePage,
    backup,
    downloadBackup: downloadBackupFile,
  };
}
