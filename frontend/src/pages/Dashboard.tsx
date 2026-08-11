import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { TableSummary, deleteSchema, getWorkspace } from "../api/schemas";
import {
  CreateColumnInput,
  DraftColumn,
  TableDraft,
  buildTableDraft,
  getTable,
} from "../api/tables";
import { ColumnUsage, getTagUsage } from "../api/tags";
import { Role } from "../api/auth";
import { CreateSchemaForm } from "../components/CreateSchemaForm";
import { CreateTableForm } from "../components/CreateTableForm";
import { DashboardSkeleton } from "../components/DashboardSkeleton";
import { MaintenancePanel } from "../components/MaintenancePanel";
import { UsersPanel } from "../components/UsersPanel";
import { TableDetail } from "../components/TableDetail";
import { TableSidebar } from "../components/TableSidebar";
import { TagsPanel } from "../components/TagsPanel";
import { WorkspaceNav, WorkspaceView } from "../components/WorkspaceNav";
import { useAuth } from "../auth/AuthProvider";
import { useMaintenance } from "../hooks/useMaintenance";
import { useSchemas } from "../hooks/useSchemas";
import { useTables } from "../hooks/useTables";
import { useTags } from "../hooks/useTags";
import { useUsers } from "../hooks/useUsers";
import {
  NOTIFICATION_DURATION_MS,
  notifyFromError,
  useNotify,
} from "../notifications/NotificationProvider";

// Yeni eklenen (henuz kaydedilmemis) taslak columns icin biricik gecici id — negatif oldugu
// icin gercek (backend'in urettigi, hep pozitif) id'lerle asla cakismaz. Modul seviyesinde:
// component yeniden render olsa da sifirlanmamali, tek sayfalik uygulamada tek Dashboard oldugu
// icin paylasilmasi sorun degil.
let nextDraftColumnId = -1;

/**
 * Ana ekran, tum uygulama state'inin (tablolar, tags, hangi tablo secili, yukleniyor mu,
 * create formu acik mi) tutuldugu yer. Alt component'lere (Sidebar/Detail/Form) hem veriyi
 * hem de "bir seye tikladiginda ne olsun" fonksiyonlarini (handler'lari) props olarak geçirir
 * — cocuk component'ler kendi state'ini tutmaz, hepsi burada toplanir (bu React'ta
 * "state lifting" denen desen).
 * <p>
 * Ortak pattern: her handle* fonksiyonu ayni sirayi izler — API'yi cagir, basariliysa
 * ilgili listeyi ({@link #refreshTablolar}/refreshTags ile DB'den yeniden) cek, kullaniciya
 * renkli bildirim goster. Optimistic update (once ekrani guncelleyip sonra API'yi cagirma)
 * yapmiyoruz; her mutasyondan sonra backend'den taze veri cekmek daha basit ve garanti dogru.
 */
interface DashboardProps {
  /**
   * App.tsx'teki bildirim paneline tiklaninca dolar: "bu tabloyu ac". Dashboard'un kendi
   * selectedId/activeView state'i App.tsx'ten disaridan degistirilemedigi icin (bkz.
   * requirement-websocket-notifications.md Faz 5 Adim 5.5) bu prop bir tetikleyici gorevi
   * gorur — deger degisince asagidaki useEffect devreye girer.
   */
  navigateToTableId?: number | null;
  /** navigateToTableId tuketildikten sonra App.tsx'in onu null'a dondurmesi icin. */
  onNavigated?: () => void;
}

export function Dashboard({ navigateToTableId, onNavigated }: DashboardProps = {}) {
  // Sema domain'inin okuma+yazma sorumlulugu useSchemas hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md). setSchemasOptimistic SADECE handleDeleteSchema'daki
  // "geri al" akisi icin var — bkz. o fonksiyondaki aciklama ve hook'un kendi javadoc'u.
  const {
    schemas,
    createSchema: createSchemaHook,
    renameSchema: renameSchemaHook,
    refresh: refreshSchemas,
    setSchemasOptimistic,
  } = useSchemas();
  // Her schema'nin altindaki tablolarin ozeti (sadece id/name/columnCount) — schema id'sine gore.
  // Bir tablonun ko"lonlarinin tam listesi burada YOK; bir tabloya tiklaninca ayrica id'siyle
  // (GET /api/tablo"lar/{id}, bkz. selectTable) cekilir.
  const [tableSummariesBySchema, setTableSummariesBySchema] = useState<
    Record<number, TableSummary[]>
  >({});
  // Table domain'inin okuma+yazma sorumlulugu useTables hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2).
  const {
    selectedTable,
    select: selectTableHook,
    clearSelection: clearTableSelection,
    create: createTableHook,
    applyChanges: applyTableChangesHook,
    deleteTable: deleteTableHook,
    changeSchema: changeTableSchemaHook,
  } = useTables();
  // Tag domain'inin okuma+yazma sorumlulugu useTags hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2). Otomatik mount-cekme YOK (bkz. hook'un javadoc'u).
  const {
    tags,
    createTag: createTagHook,
    renameTag: renameTagHook,
    deleteTag: deleteTagHook,
    refresh: refreshTags,
    setTagsOptimistic,
  } = useTags();
  // User domain'inin okuma+yazma sorumlulugu useUsers hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2). Otomatik mount-cekme YOK.
  const {
    users,
    createUser: createUserHook,
    changeUserRole: changeUserRoleHook,
    deleteUser: deleteUserHook,
    refresh: refreshUsers,
    setUsersOptimistic,
  } = useUsers();
  // Maintenance sayfasinin (bkz. requirement-maintenance-audit-backup.md) tum okuma+yazma
  // sorumlulugu useMaintenance hook'una tasindi (useUsers/useTags'le ayni kalip).
  const {
    summary: maintenanceSummary,
    health: maintenanceHealth,
    auditLogs,
    auditLogsTotal,
    page: auditLogsPage,
    pageSize: auditLogsPageSize,
    loading: maintenanceLoading,
    backingUp,
    backupList,
    refresh: refreshMaintenance,
    changeFilters: changeAuditLogFilters,
    changePage: changeAuditLogPage,
    backup: backupAuditLogsHook,
    downloadBackup: downloadBackupHook,
  } = useMaintenance();
  const { isAdmin } = useAuth();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showCreateSchemaForm, setShowCreateSchemaForm] = useState(false);
  // Sol menudeki "Şemalar"/"Tagler" gecisi — hangi ana alanin gosterildigi, sidebar/detay
  // ikilisi mi yoksa etiket listesi mi.
  const [activeView, setActiveView] = useState<WorkspaceView>("schemas");
  // Su an acik olan tablonun duzenleme taslagi ("Kaydet'e basinca hepsi birden gitsin" akisi).
  const [draft, setDraft] = useState<TableDraft | null>(null);
  const [saving, setSaving] = useState(false);
  // Context API'den gelen paylasilan bildirim fonksiyonu — bkz. NotificationProvider.
  const notify = useNotify();
  const { t } = useTranslation();

  /**
   * Sidebar'in tablo ozetlerini (tableSummariesBySchema) tek istekte tazeler — GET
   * /api/schemalar/schemaList, schema+tablo agacini backend'de tek sorguda birlestirip
   * donuyor. Eskiden schema listesi + her schema icin ayri istek (N+1) atiliyordu.
   * <p>
   * Schema listesinin kendisi ({@code schemalar}) artik useSchemas hook'undan geliyor, burada
   * setlenmiyor — ama sema sayilari (tableCount) tablo olusturma/silmeyle degistigi icin, bu
   * fonksiyon her cagrildiginda hook'un kendi verisini de ({@code refreshSchemas}) birlikte
   * tazeliyor; boylece iki kaynak (workspace agaci + sema listesi) senkron kalir.
   */
  async function refreshWorkspace() {
    const workspace = await getWorkspace();
    setTableSummariesBySchema(
      Object.fromEntries(
        workspace.map((s) => [
          s.schemaId,
          s.tableResponseList.map((t) => ({ id: t.id, name: t.name, columnCount: t.columnCount })),
        ])
      )
    );
    await refreshSchemas();
  }

  /** Bir tabloyu secip TAM detayini (columns dahil) id'siyle ceker ve duzenleme taslagini kurar. */
  async function selectTable(id: number) {
    setSelectedId(id);
    try {
      const table = await selectTableHook(id);
      setDraft(buildTableDraft(table));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.loadFailed"));
      setSelectedId(null);
      clearTableSelection();
      setDraft(null);
    }
  }

  function clearSelection() {
    setSelectedId(null);
    clearTableSelection();
    setDraft(null);
  }

  // Bildirim panelinden "bu tabloya git" istegi (bkz. DashboardProps.navigateToTableId).
  // confirmDiscardIfDirty() BILEREK cagrilmiyor: bir bildirime tiklamak acik bir kullanici
  // niyeti, kaydedilmemis degisiklik varsa bile diger handle* fonksiyonlarindaki gibi burada
  // sormuyoruz — zil ikonu App.tsx'te, "iptal" durumunda navigateToTableId'yi geri
  // eski haline dondurecek bir yol yok.
  useEffect(() => {
    if (navigateToTableId == null) {
      return;
    }
    setActiveView("schemas");
    selectTable(navigateToTableId);
    onNavigated?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigateToTableId]);

  // Bos dependency array ([]) = sadece component ilk kez ekrana geldiginde (mount) bir kez
  // calisir. Baslangicta SADECE schemalar/tablolar cekilir (varsayilan gorunum "schemalar") —
  // tags ve users, kullanici o sekmelere GIRENE kadar hic istenmez; bkz. handleChangeActiveView.
  useEffect(() => {
    refreshWorkspace()
      .catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Taslak, orijinal tablodan herhangi bir sekilde farkliysa "kirli" sayilir — Kaydet butonu
  // buna gore aktif olur, tablo/gorunum degistirirken de bu kontrol edilir.
  const isDirty = (() => {
    if (!draft || !selectedTable || selectedTable.id !== draft.tableId) {
      return false;
    }
    if (draft.name !== selectedTable.name || draft.schemaId !== selectedTable.schemaId) {
      return true;
    }
    return draft.columns.some((k) => {
      if (k.isNew || k.toDelete) {
        return true;
      }
      const ok = selectedTable.columns.find((o) => o.id === k.id);
      return !ok || ok.name !== k.name || ok.tagId !== k.tagId || ok.primaryKey !== k.primaryKey;
    });
  })();

  // Sidebar'dan surukle-birakla acik olan tabloya baska bir schema atandiysa (Kaydet'e kadar
  // bekleyen bir tasima), TableDetail'e "nereye tasinacak" bilgisini gostermesi icin hedef
  // schema'nin adini hesapliyoruz. Sadece GERCEKTEN degistiyse doluyor — draft.schemaId her
  // zaman gecerli bir schema'ya isaret eder (tablo zaten bir schema'da), o yuzden orijinalle
  // karsilastirmadan sadece draft.schemaId'ye bakmak "hicbir sey degismedi" durumunda bile
  // yanlislikla gosterirdi.
  const pendingSchemaName = (() => {
    if (!draft || !selectedTable || selectedTable.id !== draft.tableId) {
      return null;
    }
    if (draft.schemaId === selectedTable.schemaId) {
      return null;
    }
    return schemas.find((s) => s.id === draft.schemaId)?.name ?? null;
  })();

  /** Kaydedilmemis degisiklik varken baska bir tabloya/gorunume gecmeden once onay ister. */
  function confirmDiscardIfDirty(): boolean {
    return !isDirty || window.confirm(t("tabloDetail.confirmDiscardChanges"));
  }

  function handleSelectTablo(id: number) {
    if (confirmDiscardIfDirty()) {
      selectTable(id);
    }
  }

  /**
   * Her sekmeye GIRILDIGINDE (burada) ilgili veri tazelenir — component hic unmount olmadigi
   * icin bu, DB'de baska bir yerden (baska bir kullanici, baska bir sekme) yapilan degisiklikleri
   * gormenin tek yolu. Schemalar icin de ayni sebeple refreshWorkspace() cagriliyor; ilk mount'taki
   * cekim (yukarida) sadece sayfa hic acilmamisken ilk veriyi getirir. isAdmin kontrolu backend
   * zaten VIEWER/EDITOR'a 403 dondugu icin (bkz. SecurityConfig), onlar icin bu istegi hic
   * atmamak "sayfa yuklenemedi" bildirimini gereksiz yere kirletmemek anlamina gelir.
   */
  function handleChangeActiveView(view: WorkspaceView) {
    if (!confirmDiscardIfDirty()) {
      return;
    }
    setActiveView(view);
    if (view === "schemas") {
      refreshWorkspace().catch((err) =>
        notifyFromError(notify, t, err, t("notifications.loadFailed"))
      );
    } else if (view === "tags") {
      refreshTags().catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")));
    } else if (view === "users" && isAdmin) {
      refreshUsers().catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")));
    } else if (view === "maintenance" && isAdmin) {
      refreshMaintenance().catch((err) =>
        notifyFromError(notify, t, err, t("notifications.loadFailed"))
      );
    }
  }

  function handleChangeDraftName(name: string) {
    setDraft((prev) => (prev ? { ...prev, name } : prev));
  }

  function handleAddDraftColumn(input: {
    name: string;
    type: DraftColumn["type"];
    tagId: number | null;
    primaryKey: boolean;
  }) {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            columns: [
              ...prev.columns,
              { id: nextDraftColumnId--, isNew: true, toDelete: false, ...input },
            ],
          }
        : prev
    );
  }

  /** Henuz kaydedilmemis (yeni eklenen) bir kolonu listeden tamamen cikarir; var olan bir kolonda toDelete/geri-al isaretini ters cevirir. */
  function handleToggleDeleteDraftKolon(columnId: number) {
    setDraft((prev) => {
      if (!prev) {
        return prev;
      }
      const column = prev.columns.find((k) => k.id === columnId);
      if (!column) {
        return prev;
      }
      if (column.isNew) {
        return { ...prev, columns: prev.columns.filter((k) => k.id !== columnId) };
      }
      return {
        ...prev,
        columns: prev.columns.map((k) => (k.id === columnId ? { ...k, toDelete: !k.toDelete } : k)),
      };
    });
  }

  function handleChangeDraftColumnName(columnId: number, name: string) {
    setDraft((prev) =>
      prev
        ? { ...prev, columns: prev.columns.map((k) => (k.id === columnId ? { ...k, name } : k)) }
        : prev
    );
  }

  function handleChangeDraftColumnTag(columnId: number, tagId: number | null) {
    setDraft((prev) =>
      prev
        ? { ...prev, columns: prev.columns.map((k) => (k.id === columnId ? { ...k, tagId } : k)) }
        : prev
    );
  }

  function handleChangeDraftColumnPrimaryKey(columnId: number, primaryKey: boolean) {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            columns: prev.columns.map((k) => (k.id === columnId ? { ...k, primaryKey } : k)),
          }
        : prev
    );
  }

  function handleDiscardDraft() {
    if (selectedTable && draft && selectedTable.id === draft.tableId) {
      setDraft(buildTableDraft(selectedTable));
    }
  }

  /**
   * Taslagi orijinal tabloyla karsilastirip diff'i hesaplar ve TEK bir applyTableChanges
   * cagrisiyla gonderir — backend bunu tek transaction'da uygular (bkz. TabloService.applyChanges).
   */
  async function handleSaveDraft() {
    if (!draft || !selectedTable || selectedTable.id !== draft.tableId) {
      return;
    }
    const orijinal = selectedTable;
    setSaving(true);
    try {
      const guncel = await applyTableChangesHook(draft.tableId, {
        newName: draft.name !== orijinal.name ? draft.name : null,
        newSchemaId: draft.schemaId !== orijinal.schemaId ? draft.schemaId : null,
        columnIdsToDelete: draft.columns.filter((k) => !k.isNew && k.toDelete).map((k) => k.id),
        columnsToAdd: draft.columns
          .filter((k) => k.isNew)
          .map((k) => ({ name: k.name, type: k.type, tagId: k.tagId, primaryKey: k.primaryKey })),
        columnsToUpdate: draft.columns
          .filter((k) => !k.isNew && !k.toDelete)
          .filter((k) => {
            const ok = orijinal.columns.find((o) => o.id === k.id);
            return (
              ok && (ok.name !== k.name || ok.tagId !== k.tagId || ok.primaryKey !== k.primaryKey)
            );
          })
          .map((k) => ({
            columnId: k.id,
            newName: k.name,
            newTagId: k.tagId,
            newPrimaryKey: k.primaryKey,
          })),
      });
      setDraft(buildTableDraft(guncel));
      await refreshWorkspace();
      notify(200, t("notifications.tableChangesSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableChangesSaveFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function handleCreate(name: string, columns: CreateColumnInput[], schemaId: number) {
    try {
      const created = await createTableHook(name, columns, schemaId);
      await refreshWorkspace();
      setSelectedId(created.id);
      setDraft(buildTableDraft(created));
      setShowCreateForm(false);
      notify(201, t("notifications.tableCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableCreateFailed"));
    }
  }

  async function handleCreateSchema(name: string) {
    try {
      const created = await createSchemaHook(name);
      setShowCreateSchemaForm(false);
      notify(201, t("notifications.schemaCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.schemaCreateFailed"));
    }
  }

  async function handleRenameSchema(id: number, name: string) {
    try {
      await renameSchemaHook(id, name);
      notify(200, t("notifications.schemaRenamed"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.schemaRenameFailed"));
    }
  }

  /**
   * handleDeleteTablo ile ayni geri-alinabilir-silme deseni (bkz. oradaki aciklama) — ama burada
   * silinen bir schema, icindeki TUM tablolari da beraberinde goturuyor (gercek DROP SCHEMA
   * CASCADE). O yuzden secili tablo bu schema'nin icindeyse secimi de temizliyoruz; sidebar
   * ekstra bir onay zaten TableSidebar icinde (window.confirm ile, tablo sayisini gostererek)
   * gosteriliyor, burasi sadece asil silme/geri-alma mekanigini yonetiyor.
   */
  function handleDeleteSchema(id: number) {
    const tableIdsInSchema = new Set((tableSummariesBySchema[id] ?? []).map((tbl) => tbl.id));
    setSchemasOptimistic((prev) => prev.filter((s) => s.id !== id));
    setTableSummariesBySchema((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
    if (selectedId !== null && tableIdsInSchema.has(selectedId)) {
      clearSelection();
    }

    const timerId = window.setTimeout(async () => {
      try {
        await deleteSchema(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.schemaDeleteFailed"));
      } finally {
        await refreshWorkspace();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.schemaDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshWorkspace();
      },
    });
  }

  /**
   * Geri alinabilir silme: backend'de gercek bir DROP TABLE calistigi icin, silindikten SONRA
   * geri almanin bir yolu yok. O yuzden burada gercek API cagrisini hemen yapmiyoruz — once
   * tabloyu ekrandan (iyimser/optimistic olarak) kaldirip "Geri Al" butonlu bir bildirim
   * gosteriyoruz; NOTIFICATION_DURATION_MS boyunca "Geri Al"a basilmazsa, o zaman gercek
   * silme istegini gonderiyoruz. Basilirsa, zamanlayici iptal edilir ve hicbir seye
   * dokunulmamis gibi tablo geri gelir (backend'de zaten silinmemistir).
   */
  function handleDeleteTablo(id: number) {
    const schemaEntry = Object.entries(tableSummariesBySchema).find(([, list]) =>
      list.some((tbl) => tbl.id === id)
    );
    if (schemaEntry) {
      const [schemaIdKey, list] = schemaEntry;
      setTableSummariesBySchema((prev) => ({
        ...prev,
        [Number(schemaIdKey)]: list.filter((tbl) => tbl.id !== id),
      }));
    }
    if (selectedId === id) {
      clearSelection();
    }

    const timerId = window.setTimeout(async () => {
      try {
        await deleteTableHook(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.tableDeleteFailed"));
      } finally {
        await refreshWorkspace();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.tableDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshWorkspace();
      },
    });
  }

  /**
   * Sidebar'dan surukle-birakla tetiklenir. Suruklenen tablo su an ACIK olan (taslagi bulunan)
   * tabloysa, bu da o taslagin bir parcasi olur — Kaydet'e kadar hicbir yere gitmez. Baska (acik
   * olmayan) bir tablo suruklendiyse, bugunku gibi aninda uygulanir (o tablo icin acik bir
   * duzenleme oturumu yok, ertelenecek bir sey de yok).
   */
  async function handleChangeTableSchema(id: number, schemaId: number) {
    if (draft && draft.tableId === id) {
      setDraft((prev) => (prev ? { ...prev, schemaId } : prev));
      return;
    }
    try {
      await changeTableSchemaHook(id, schemaId);
      await refreshWorkspace();
      notify(200, t("notifications.tableSchemaChanged"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableSchemaChangeFailed"));
    }
  }

  async function handleCreateTag(name: string) {
    try {
      const created = await createTagHook(name);
      await refreshTags();
      notify(201, t("notifications.tagCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tagCreateFailed"));
    }
  }

  /**
   * TagsPanel'e prop olarak geciyoruz, kendisi API'ye dokunmuyor (diger tum handle*
   * fonksiyonlarindaki ayni desen). Basarisiz olursa bos liste doner — panel bunu "kullanim
   * yok" ile ayni sekilde gosterir, ayrica notify ile hata bildirimi de cikar.
   */
  async function handleLoadTagUsage(tagId: number): Promise<ColumnUsage[]> {
    try {
      return await getTagUsage(tagId);
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tagUsageLoadFailed"));
      return [];
    }
  }

  async function handleRenameTag(id: number, name: string) {
    try {
      await renameTagHook(id, name);
      await refreshTags();
      notify(200, t("notifications.tagRenamed"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tagRenameFailed"));
    }
  }

  /**
   * handleDeleteTablo ile ayni geri-alinabilir-silme deseni (bkz. oradaki aciklama) — onceden
   * tag/kullanici silme dogrudan (geri alinamaz) calisiyordu, tutarlilik icin diger silmelerle
   * ayni "Geri Al" penceresine cekildi.
   */
  function handleDeleteTag(id: number) {
    setTagsOptimistic((prev) => prev.filter((tag) => tag.id !== id));

    const timerId = window.setTimeout(async () => {
      try {
        await deleteTagHook(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.tagDeleteFailed"));
      } finally {
        await refreshTags();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.tagDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshTags();
      },
    });
  }

  async function handleCreateUser(username: string, password: string, role: Role) {
    try {
      const created = await createUserHook(username, password, role);
      await refreshUsers();
      notify(201, t("notifications.kullaniciCreated", { name: created.username }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.kullaniciCreateFailed"));
    }
  }

  async function handleChangeUserRole(id: number, role: Role) {
    try {
      await changeUserRoleHook(id, role);
      await refreshUsers();
      notify(200, t("notifications.kullaniciRolChanged"));
    } catch (err) {
      // Basarisiz olursa (ör. CONFLICT_LAST_ADMIN) listeyi YENIDEN CEKMIYORUZ — kullanicilar
      // state'i degismedigi icin <select> zaten backend'in kabul ettigi son degere geri doner,
      // ekstra bir "geri al" mantigi gerekmiyor.
      notifyFromError(notify, t, err, t("notifications.kullaniciRolChangeFailed"));
    }
  }

  function handleDeleteKullanici(id: number) {
    setUsersOptimistic((prev) => prev.filter((user) => user.id !== id));

    const timerId = window.setTimeout(async () => {
      try {
        await deleteUserHook(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.kullaniciDeleteFailed"));
      } finally {
        await refreshUsers();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.kullaniciDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshUsers();
      },
    });
  }

  async function handleBackupAuditLogs() {
    try {
      const result = await backupAuditLogsHook();
      notify(200, t("notifications.auditLogBackupSucceeded", { count: result.rowCount }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.auditLogBackupFailed"));
    }
  }

  async function handleDownloadBackup(key: string) {
    try {
      await downloadBackupHook(key);
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.auditLogBackupDownloadFailed"));
    }
  }

  if (loading) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="dashboard flex flex-1">
      <WorkspaceNav active={activeView} onChange={handleChangeActiveView} />

      {activeView === "tags" && (
        <TagsPanel
          tags={tags}
          onLoadUsage={handleLoadTagUsage}
          onRename={handleRenameTag}
          onDelete={handleDeleteTag}
        />
      )}

      {activeView === "users" && (
        <UsersPanel
          users={users}
          onCreate={handleCreateUser}
          onChangeRole={handleChangeUserRole}
          onDelete={handleDeleteKullanici}
        />
      )}

      {activeView === "maintenance" && (
        <MaintenancePanel
          summary={maintenanceSummary}
          health={maintenanceHealth}
          auditLogs={auditLogs}
          auditLogsTotal={auditLogsTotal}
          page={auditLogsPage}
          pageSize={auditLogsPageSize}
          loading={maintenanceLoading}
          backingUp={backingUp}
          backupList={backupList}
          onFilterChange={changeAuditLogFilters}
          onPageChange={changeAuditLogPage}
          onBackup={handleBackupAuditLogs}
          onDownloadBackup={handleDownloadBackup}
        />
      )}

      {activeView === "schemas" && (
        <>
          <TableSidebar
            schemas={schemas}
            tableSummariesBySchema={tableSummariesBySchema}
            selectedId={selectedId}
            onSelect={handleSelectTablo}
            onCreateClick={() => setShowCreateForm(true)}
            onCreateSchemaClick={() => setShowCreateSchemaForm(true)}
            onRenameSchema={handleRenameSchema}
            onDeleteSchema={handleDeleteSchema}
            onChangeTableSchema={handleChangeTableSchema}
            // TableSidebar'in kendi lazy-yukleme onbellegi (kolonlarByTabloId) icin — useTables
            // hook'unun selectedTable state'iyle ilgisi yok, bilerek dogrudan api fonksiyonu.
            onLoadColumns={getTable}
          />

          {draft ? (
            <TableDetail
              draft={draft}
              tags={tags}
              isDirty={isDirty}
              saving={saving}
              pendingSchemaName={pendingSchemaName}
              onChangeName={handleChangeDraftName}
              onAddColumn={handleAddDraftColumn}
              onToggleDeleteKolon={handleToggleDeleteDraftKolon}
              onChangeColumnName={handleChangeDraftColumnName}
              onChangeColumnTag={handleChangeDraftColumnTag}
              onChangeColumnPrimaryKey={handleChangeDraftColumnPrimaryKey}
              onSave={handleSaveDraft}
              onDiscard={handleDiscardDraft}
              onDeleteTablo={handleDeleteTablo}
              onCreateTag={handleCreateTag}
            />
          ) : (
            <section className="detail-panel fadeinup animation-duration-200">
              <div className="empty-state flex align-items-center justify-content-center h-full">
                <p className="empty-state-line">
                  <span className="empty-state-comment">-- </span>
                  {t("dashboard.selectTable")}
                  <span className="empty-state-cursor">_</span>
                </p>
              </div>
            </section>
          )}
        </>
      )}

      {showCreateForm && (
        <CreateTableForm
          schemas={schemas}
          onSubmit={handleCreate}
          onClose={() => setShowCreateForm(false)}
        />
      )}

      {showCreateSchemaForm && (
        <CreateSchemaForm
          onSubmit={handleCreateSchema}
          onClose={() => setShowCreateSchemaForm(false)}
        />
      )}
    </div>
  );
}
