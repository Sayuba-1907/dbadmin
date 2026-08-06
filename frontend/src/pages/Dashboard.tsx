import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { TabloSummary, deleteSchema, getWorkspace } from "../api/schemas";
import {
  CreateKolonInput,
  DraftKolon,
  TabloDraft,
  buildTabloDraft,
  getTablo,
} from "../api/tablolar";
import { KolonUsage, getTagUsage } from "../api/tags";
import { Rol } from "../api/auth";
import { CreateSchemaForm } from "../components/CreateSchemaForm";
import { CreateTabloForm } from "../components/CreateTabloForm";
import { DashboardSkeleton } from "../components/DashboardSkeleton";
import { KullanicilarPanel } from "../components/KullanicilarPanel";
import { TabloDetail } from "../components/TabloDetail";
import { TabloSidebar } from "../components/TabloSidebar";
import { TaglerPanel } from "../components/TaglerPanel";
import { WorkspaceNav, WorkspaceView } from "../components/WorkspaceNav";
import { useAuth } from "../auth/AuthProvider";
import { useSchemalar } from "../hooks/useSchemalar";
import { useTablolar } from "../hooks/useTablolar";
import { useTags } from "../hooks/useTags";
import { useKullanicilar } from "../hooks/useKullanicilar";
import {
  NOTIFICATION_DURATION_MS,
  notifyFromError,
  useNotify,
} from "../notifications/NotificationProvider";

// Yeni eklenen (henuz kaydedilmemis) taslak kolonlar icin biricik gecici id — negatif oldugu
// icin gercek (backend'in urettigi, hep pozitif) id'lerle asla cakismaz. Modul seviyesinde:
// component yeniden render olsa da sifirlanmamali, tek sayfalik uygulamada tek Dashboard oldugu
// icin paylasilmasi sorun degil.
let nextDraftKolonId = -1;

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
export function Dashboard() {
  // Sema domain'inin okuma+yazma sorumlulugu useSchemalar hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md). setSchemalarOptimistic SADECE handleDeleteSchema'daki
  // "geri al" akisi icin var — bkz. o fonksiyondaki aciklama ve hook'un kendi javadoc'u.
  const {
    schemalar,
    createSchema: createSchemaHook,
    renameSchema: renameSchemaHook,
    yenile: yenileSchemalar,
    setSchemalarOptimistic,
  } = useSchemalar();
  // Her schema'nin altindaki tablolarin ozeti (sadece id/name/kolonSayisi) — schema id'sine gore.
  // Bir tablonun ko"lonlarinin tam listesi burada YOK; bir tabloya tiklaninca ayrica id'siyle
  // (GET /api/tablo"lar/{id}, bkz. selectTablo) cekilir.
  const [tabloSummariesBySchema, setTabloSummariesBySchema] = useState<
    Record<number, TabloSummary[]>
  >({});
  // Tablo domain'inin okuma+yazma sorumlulugu useTablolar hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2).
  const {
    selectedTablo,
    select: selectTabloHook,
    clearSelection: clearTabloSelection,
    create: createTabloHook,
    applyChanges: applyTabloChangesHook,
    deleteTablo: deleteTabloHook,
    changeSchema: changeTabloSchemaHook,
  } = useTablolar();
  // Tag domain'inin okuma+yazma sorumlulugu useTags hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2). Otomatik mount-cekme YOK (bkz. hook'un javadoc'u).
  const {
    tags,
    createTag: createTagHook,
    renameTag: renameTagHook,
    deleteTag: deleteTagHook,
    yenile: yenileTags,
    setTagsOptimistic,
  } = useTags();
  // Kullanici domain'inin okuma+yazma sorumlulugu useKullanicilar hook'una tasindi (bkz.
  // requirement-react-custom-hooks.md Faz 2). Otomatik mount-cekme YOK.
  const {
    kullanicilar,
    createKullanici: createKullaniciHook,
    changeKullaniciRol: changeKullaniciRolHook,
    deleteKullanici: deleteKullaniciHook,
    yenile: yenileKullanicilar,
    setKullanicilarOptimistic,
  } = useKullanicilar();
  const { isAdmin } = useAuth();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showCreateSchemaForm, setShowCreateSchemaForm] = useState(false);
  // Sol menudeki "Şemalar"/"Tagler" gecisi — hangi ana alanin gosterildigi, sidebar/detay
  // ikilisi mi yoksa etiket listesi mi.
  const [activeView, setActiveView] = useState<WorkspaceView>("schemalar");
  // Su an acik olan tablonun duzenleme taslagi ("Kaydet'e basinca hepsi birden gitsin" akisi).
  const [draft, setDraft] = useState<TabloDraft | null>(null);
  const [saving, setSaving] = useState(false);
  // Context API'den gelen paylasilan bildirim fonksiyonu — bkz. NotificationProvider.
  const notify = useNotify();
  const { t } = useTranslation();

  /**
   * Sidebar'in tablo ozetlerini (tabloSummariesBySchema) tek istekte tazeler — GET
   * /api/schemalar/schemaList, schema+tablo agacini backend'de tek sorguda birlestirip
   * donuyor. Eskiden schema listesi + her schema icin ayri istek (N+1) atiliyordu.
   * <p>
   * Schema listesinin kendisi ({@code schemalar}) artik useSchemalar hook'undan geliyor, burada
   * setlenmiyor — ama sema sayilari (tabloSayisi) tablo olusturma/silmeyle degistigi icin, bu
   * fonksiyon her cagrildiginda hook'un kendi verisini de ({@code yenileSchemalar}) birlikte
   * tazeliyor; boylece iki kaynak (workspace agaci + sema listesi) senkron kalir.
   */
  async function refreshWorkspace() {
    const workspace = await getWorkspace();
    setTabloSummariesBySchema(
      Object.fromEntries(
        workspace.map((s) => [
          s.schemaId,
          s.tableResponseList.map((t) => ({ id: t.id, name: t.name, kolonSayisi: t.columnCount })),
        ])
      )
    );
    await yenileSchemalar();
  }

  /** Bir tabloyu secip TAM detayini (kolonlar dahil) id'siyle ceker ve duzenleme taslagini kurar. */
  async function selectTablo(id: number) {
    setSelectedId(id);
    try {
      const tablo = await selectTabloHook(id);
      setDraft(buildTabloDraft(tablo));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.loadFailed"));
      setSelectedId(null);
      clearTabloSelection();
      setDraft(null);
    }
  }

  function clearSelection() {
    setSelectedId(null);
    clearTabloSelection();
    setDraft(null);
  }

  // Bos dependency array ([]) = sadece component ilk kez ekrana geldiginde (mount) bir kez
  // calisir. Baslangicta SADECE schemalar/tablolar cekilir (varsayilan gorunum "schemalar") —
  // tags ve kullanicilar, kullanici o sekmelere GIRENE kadar hic istenmez; bkz. handleChangeActiveView.
  useEffect(() => {
    refreshWorkspace()
      .catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Taslak, orijinal tablodan herhangi bir sekilde farkliysa "kirli" sayilir — Kaydet butonu
  // buna gore aktif olur, tablo/gorunum degistirirken de bu kontrol edilir.
  const isDirty = (() => {
    if (!draft || !selectedTablo || selectedTablo.id !== draft.tabloId) {
      return false;
    }
    if (draft.name !== selectedTablo.name || draft.schemaId !== selectedTablo.schemaId) {
      return true;
    }
    return draft.kolonlar.some((k) => {
      if (k.isNew || k.silinecek) {
        return true;
      }
      const ok = selectedTablo.kolonlar.find((o) => o.id === k.id);
      return !ok || ok.name !== k.name || ok.tagId !== k.tagId || ok.primaryKey !== k.primaryKey;
    });
  })();

  // Sidebar'dan surukle-birakla acik olan tabloya baska bir schema atandiysa (Kaydet'e kadar
  // bekleyen bir tasima), TabloDetail'e "nereye tasinacak" bilgisini gostermesi icin hedef
  // schema'nin adini hesapliyoruz. Sadece GERCEKTEN degistiyse doluyor — draft.schemaId her
  // zaman gecerli bir schema'ya isaret eder (tablo zaten bir schema'da), o yuzden orijinalle
  // karsilastirmadan sadece draft.schemaId'ye bakmak "hicbir sey degismedi" durumunda bile
  // yanlislikla gosterirdi.
  const pendingSchemaName = (() => {
    if (!draft || !selectedTablo || selectedTablo.id !== draft.tabloId) {
      return null;
    }
    if (draft.schemaId === selectedTablo.schemaId) {
      return null;
    }
    return schemalar.find((s) => s.id === draft.schemaId)?.name ?? null;
  })();

  /** Kaydedilmemis degisiklik varken baska bir tabloya/gorunume gecmeden once onay ister. */
  function confirmDiscardIfDirty(): boolean {
    return !isDirty || window.confirm(t("tabloDetail.confirmDiscardChanges"));
  }

  function handleSelectTablo(id: number) {
    if (confirmDiscardIfDirty()) {
      selectTablo(id);
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
    if (view === "schemalar") {
      refreshWorkspace().catch((err) =>
        notifyFromError(notify, t, err, t("notifications.loadFailed"))
      );
    } else if (view === "tagler") {
      yenileTags().catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")));
    } else if (view === "kullanicilar" && isAdmin) {
      yenileKullanicilar().catch((err) =>
        notifyFromError(notify, t, err, t("notifications.loadFailed"))
      );
    }
  }

  function handleChangeDraftName(name: string) {
    setDraft((prev) => (prev ? { ...prev, name } : prev));
  }

  function handleAddDraftKolon(input: {
    name: string;
    type: DraftKolon["type"];
    tagId: number | null;
    primaryKey: boolean;
  }) {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            kolonlar: [
              ...prev.kolonlar,
              { id: nextDraftKolonId--, isNew: true, silinecek: false, ...input },
            ],
          }
        : prev
    );
  }

  /** Henuz kaydedilmemis (yeni eklenen) bir kolonu listeden tamamen cikarir; var olan bir kolonda silinecek/geri-al isaretini ters cevirir. */
  function handleToggleDeleteDraftKolon(kolonId: number) {
    setDraft((prev) => {
      if (!prev) {
        return prev;
      }
      const kolon = prev.kolonlar.find((k) => k.id === kolonId);
      if (!kolon) {
        return prev;
      }
      if (kolon.isNew) {
        return { ...prev, kolonlar: prev.kolonlar.filter((k) => k.id !== kolonId) };
      }
      return {
        ...prev,
        kolonlar: prev.kolonlar.map((k) =>
          k.id === kolonId ? { ...k, silinecek: !k.silinecek } : k
        ),
      };
    });
  }

  function handleChangeDraftKolonName(kolonId: number, name: string) {
    setDraft((prev) =>
      prev
        ? { ...prev, kolonlar: prev.kolonlar.map((k) => (k.id === kolonId ? { ...k, name } : k)) }
        : prev
    );
  }

  function handleChangeDraftKolonTag(kolonId: number, tagId: number | null) {
    setDraft((prev) =>
      prev
        ? { ...prev, kolonlar: prev.kolonlar.map((k) => (k.id === kolonId ? { ...k, tagId } : k)) }
        : prev
    );
  }

  function handleChangeDraftKolonPrimaryKey(kolonId: number, primaryKey: boolean) {
    setDraft((prev) =>
      prev
        ? {
            ...prev,
            kolonlar: prev.kolonlar.map((k) => (k.id === kolonId ? { ...k, primaryKey } : k)),
          }
        : prev
    );
  }

  function handleDiscardDraft() {
    if (selectedTablo && draft && selectedTablo.id === draft.tabloId) {
      setDraft(buildTabloDraft(selectedTablo));
    }
  }

  /**
   * Taslagi orijinal tabloyla karsilastirip diff'i hesaplar ve TEK bir applyTabloChanges
   * cagrisiyla gonderir — backend bunu tek transaction'da uygular (bkz. TabloService.applyChanges).
   */
  async function handleSaveDraft() {
    if (!draft || !selectedTablo || selectedTablo.id !== draft.tabloId) {
      return;
    }
    const orijinal = selectedTablo;
    setSaving(true);
    try {
      const guncel = await applyTabloChangesHook(draft.tabloId, {
        yeniIsim: draft.name !== orijinal.name ? draft.name : null,
        yeniSchemaId: draft.schemaId !== orijinal.schemaId ? draft.schemaId : null,
        silinecekKolonIdler: draft.kolonlar.filter((k) => !k.isNew && k.silinecek).map((k) => k.id),
        eklenecekKolonlar: draft.kolonlar
          .filter((k) => k.isNew)
          .map((k) => ({ name: k.name, type: k.type, tagId: k.tagId, primaryKey: k.primaryKey })),
        guncellenecekKolonlar: draft.kolonlar
          .filter((k) => !k.isNew && !k.silinecek)
          .filter((k) => {
            const ok = orijinal.kolonlar.find((o) => o.id === k.id);
            return (
              ok && (ok.name !== k.name || ok.tagId !== k.tagId || ok.primaryKey !== k.primaryKey)
            );
          })
          .map((k) => ({
            kolonId: k.id,
            yeniIsim: k.name,
            yeniTagId: k.tagId,
            yeniPrimaryKey: k.primaryKey,
          })),
      });
      setDraft(buildTabloDraft(guncel));
      await refreshWorkspace();
      notify(200, t("notifications.tableChangesSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableChangesSaveFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function handleCreate(name: string, kolonlar: CreateKolonInput[], schemaId: number) {
    try {
      const created = await createTabloHook(name, kolonlar, schemaId);
      await refreshWorkspace();
      setSelectedId(created.id);
      setDraft(buildTabloDraft(created));
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
   * ekstra bir onay zaten TabloSidebar icinde (window.confirm ile, tablo sayisini gostererek)
   * gosteriliyor, burasi sadece asil silme/geri-alma mekanigini yonetiyor.
   */
  function handleDeleteSchema(id: number) {
    const tableIdsInSchema = new Set((tabloSummariesBySchema[id] ?? []).map((tbl) => tbl.id));
    setSchemalarOptimistic((prev) => prev.filter((s) => s.id !== id));
    setTabloSummariesBySchema((prev) => {
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
    const schemaEntry = Object.entries(tabloSummariesBySchema).find(([, list]) =>
      list.some((tbl) => tbl.id === id)
    );
    if (schemaEntry) {
      const [schemaIdKey, list] = schemaEntry;
      setTabloSummariesBySchema((prev) => ({
        ...prev,
        [Number(schemaIdKey)]: list.filter((tbl) => tbl.id !== id),
      }));
    }
    if (selectedId === id) {
      clearSelection();
    }

    const timerId = window.setTimeout(async () => {
      try {
        await deleteTabloHook(id);
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
  async function handleChangeTabloSchema(id: number, schemaId: number) {
    if (draft && draft.tabloId === id) {
      setDraft((prev) => (prev ? { ...prev, schemaId } : prev));
      return;
    }
    try {
      await changeTabloSchemaHook(id, schemaId);
      await refreshWorkspace();
      notify(200, t("notifications.tableSchemaChanged"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableSchemaChangeFailed"));
    }
  }

  async function handleCreateTag(name: string) {
    try {
      const created = await createTagHook(name);
      await yenileTags();
      notify(201, t("notifications.tagCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tagCreateFailed"));
    }
  }

  /**
   * TaglerPanel'e prop olarak geciyoruz, kendisi API'ye dokunmuyor (diger tum handle*
   * fonksiyonlarindaki ayni desen). Basarisiz olursa bos liste doner — panel bunu "kullanim
   * yok" ile ayni sekilde gosterir, ayrica notify ile hata bildirimi de cikar.
   */
  async function handleLoadTagUsage(tagId: number): Promise<KolonUsage[]> {
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
      await yenileTags();
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
        await yenileTags();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.tagDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        yenileTags();
      },
    });
  }

  async function handleCreateKullanici(kullaniciAdi: string, parola: string, rol: Rol) {
    try {
      const created = await createKullaniciHook(kullaniciAdi, parola, rol);
      await yenileKullanicilar();
      notify(201, t("notifications.kullaniciCreated", { name: created.kullaniciAdi }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.kullaniciCreateFailed"));
    }
  }

  async function handleChangeKullaniciRol(id: number, rol: Rol) {
    try {
      await changeKullaniciRolHook(id, rol);
      await yenileKullanicilar();
      notify(200, t("notifications.kullaniciRolChanged"));
    } catch (err) {
      // Basarisiz olursa (ör. CONFLICT_LAST_ADMIN) listeyi YENIDEN CEKMIYORUZ — kullanicilar
      // state'i degismedigi icin <select> zaten backend'in kabul ettigi son degere geri doner,
      // ekstra bir "geri al" mantigi gerekmiyor.
      notifyFromError(notify, t, err, t("notifications.kullaniciRolChangeFailed"));
    }
  }

  function handleDeleteKullanici(id: number) {
    setKullanicilarOptimistic((prev) => prev.filter((kullanici) => kullanici.id !== id));

    const timerId = window.setTimeout(async () => {
      try {
        await deleteKullaniciHook(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.kullaniciDeleteFailed"));
      } finally {
        await yenileKullanicilar();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.kullaniciDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        yenileKullanicilar();
      },
    });
  }

  if (loading) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="dashboard flex flex-1">
      <WorkspaceNav active={activeView} onChange={handleChangeActiveView} />

      {activeView === "tagler" && (
        <TaglerPanel
          tags={tags}
          onLoadUsage={handleLoadTagUsage}
          onRename={handleRenameTag}
          onDelete={handleDeleteTag}
        />
      )}

      {activeView === "kullanicilar" && (
        <KullanicilarPanel
          kullanicilar={kullanicilar}
          onCreate={handleCreateKullanici}
          onChangeRol={handleChangeKullaniciRol}
          onDelete={handleDeleteKullanici}
        />
      )}

      {activeView === "schemalar" && (
        <>
          <TabloSidebar
            schemalar={schemalar}
            tabloSummariesBySchema={tabloSummariesBySchema}
            selectedId={selectedId}
            onSelect={handleSelectTablo}
            onCreateClick={() => setShowCreateForm(true)}
            onCreateSchemaClick={() => setShowCreateSchemaForm(true)}
            onRenameSchema={handleRenameSchema}
            onDeleteSchema={handleDeleteSchema}
            onChangeTabloSchema={handleChangeTabloSchema}
            // TabloSidebar'in kendi lazy-yukleme onbellegi (kolonlarByTabloId) icin — useTablolar
            // hook'unun selectedTablo state'iyle ilgisi yok, bilerek dogrudan api fonksiyonu.
            onLoadKolonlar={getTablo}
          />

          {draft ? (
            <TabloDetail
              draft={draft}
              tags={tags}
              isDirty={isDirty}
              saving={saving}
              pendingSchemaName={pendingSchemaName}
              onChangeName={handleChangeDraftName}
              onAddKolon={handleAddDraftKolon}
              onToggleDeleteKolon={handleToggleDeleteDraftKolon}
              onChangeKolonName={handleChangeDraftKolonName}
              onChangeKolonTag={handleChangeDraftKolonTag}
              onChangeKolonPrimaryKey={handleChangeDraftKolonPrimaryKey}
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
        <CreateTabloForm
          schemalar={schemalar}
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
