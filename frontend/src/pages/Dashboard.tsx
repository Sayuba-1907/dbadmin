import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Schema, createSchema, deleteSchema, getSchemalar, renameSchema } from "../api/schemas";
import {
  CreateKolonInput,
  DraftKolon,
  Tablo,
  TabloDraft,
  applyTabloChanges,
  buildTabloDraft,
  changeTabloSchema,
  createTablo,
  deleteTablo,
  getTablolar,
} from "../api/tablolar";
import { KolonUsage, Tag, createTag, getTagUsage, getTags } from "../api/tags";
import { Rol } from "../api/auth";
import {
  Kullanici,
  changeKullaniciRol,
  createKullanici,
  deleteKullanici,
  getKullanicilar,
} from "../api/kullanicilar";
import { CreateSchemaForm } from "../components/CreateSchemaForm";
import { CreateTabloForm } from "../components/CreateTabloForm";
import { KullanicilarPanel } from "../components/KullanicilarPanel";
import { TabloDetail } from "../components/TabloDetail";
import { TabloSidebar } from "../components/TabloSidebar";
import { TaglerPanel } from "../components/TaglerPanel";
import { WorkspaceNav, WorkspaceView } from "../components/WorkspaceNav";
import { useAuth } from "../auth/AuthProvider";
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
  const [tablolar, setTablolar] = useState<Tablo[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [schemalar, setSchemalar] = useState<Schema[]>([]);
  const [kullanicilar, setKullanicilar] = useState<Kullanici[]>([]);
  const { isAdmin } = useAuth();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showCreateSchemaForm, setShowCreateSchemaForm] = useState(false);
  // Sol menudeki "Şemalar"/"Tagler" gecisi — hangi ana alanin gosterildigi, sidebar/detay
  // ikilisi mi yoksa etiket listesi mi.
  const [activeView, setActiveView] = useState<WorkspaceView>("schemalar");
  // Su an acik olan tablonun duzenleme taslagi ("Kaydet'e basinca hepsi birden gitsin" akisi).
  // selectedId disinda hicbir sey (ör. arka planda tablolar yenilenmesi) bunu sifirlamamali,
  // yoksa yarim kalan bir duzenleme kaybolur.
  const [draft, setDraft] = useState<TabloDraft | null>(null);
  const [saving, setSaving] = useState(false);
  // Context API'den gelen paylasilan bildirim fonksiyonu — bkz. NotificationProvider.
  const notify = useNotify();
  const { t } = useTranslation();

  async function refreshTablolar() {
    const data = await getTablolar();
    setTablolar(data);
    return data;
  }

  async function refreshTags() {
    const data = await getTags();
    setTags(data);
    return data;
  }

  async function refreshSchemalar() {
    const data = await getSchemalar();
    setSchemalar(data);
    return data;
  }

  async function refreshKullanicilar() {
    const data = await getKullanicilar();
    setKullanicilar(data);
    return data;
  }

  // Bos dependency array ([]) = sadece component ilk kez ekrana geldiginde (mount) bir kez
  // calisir, "sayfa acilinca ilk veriyi cek" anlaminda. exhaustive-deps kurali normalde
  // refreshTablolar/refreshTags/refreshSchemalar/isAdmin/notify'i de listeye zorlar; onlar her
  // render'da yeniden olusturuldugu icin sonsuz donguye sokmemek adina bilerek kapatilmis.
  useEffect(() => {
    // Kullanici listesi sadece ADMIN icin cekilir — backend zaten VIEWER/EDITOR'a 403 donuyor
    // (bkz. SecurityConfig), onlar icin bu istegi hic atmamak "sayfa yuklenemedi" bildirimini
    // gereksiz yere kirletmemek anlamina gelir.
    const istekler: Promise<unknown>[] = [refreshTablolar(), refreshTags(), refreshSchemalar()];
    if (isAdmin) {
      istekler.push(refreshKullanicilar());
    }
    Promise.all(istekler)
      .catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // selectedId degisince taslak o tablodan yeniden kurulur. Bilerek SADECE selectedId'ye bagli:
  // tablolar arka planda (baska bir aksiyon yuzunden) yenilenirse bu efekt TEKRAR CALISMAMALI,
  // yoksa acik olan tablonun uzerindeki kaydedilmemis degisiklikler sessizce silinirdi.
  useEffect(() => {
    if (selectedId === null) {
      setDraft(null);
      return;
    }
    const tablo = tablolar.find((tbl) => tbl.id === selectedId);
    setDraft(tablo ? buildTabloDraft(tablo) : null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  // Taslak, orijinal tablodan herhangi bir sekilde farkliysa "kirli" sayilir — Kaydet butonu
  // buna gore aktif olur, tablo/gorunum degistirirken de bu kontrol edilir.
  const isDirty = (() => {
    if (!draft) {
      return false;
    }
    const orijinal = tablolar.find((tbl) => tbl.id === draft.tabloId);
    if (!orijinal) {
      return false;
    }
    if (draft.name !== orijinal.name || draft.schemaId !== orijinal.schemaId) {
      return true;
    }
    return draft.kolonlar.some((k) => {
      if (k.isNew || k.silinecek) {
        return true;
      }
      const ok = orijinal.kolonlar.find((o) => o.id === k.id);
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
    if (!draft) {
      return null;
    }
    const orijinal = tablolar.find((tbl) => tbl.id === draft.tabloId);
    if (!orijinal || draft.schemaId === orijinal.schemaId) {
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
      setSelectedId(id);
    }
  }

  function handleChangeActiveView(view: WorkspaceView) {
    if (confirmDiscardIfDirty()) {
      setActiveView(view);
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
    const orijinal = tablolar.find((tbl) => tbl.id === draft?.tabloId);
    if (orijinal) {
      setDraft(buildTabloDraft(orijinal));
    }
  }

  /**
   * Taslagi orijinal tabloyla karsilastirip diff'i hesaplar ve TEK bir applyTabloChanges
   * cagrisiyla gonderir — backend bunu tek transaction'da uygular (bkz. TabloService.applyChanges).
   */
  async function handleSaveDraft() {
    if (!draft) {
      return;
    }
    const orijinal = tablolar.find((tbl) => tbl.id === draft.tabloId);
    if (!orijinal) {
      return;
    }
    setSaving(true);
    try {
      const guncel = await applyTabloChanges(draft.tabloId, {
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
      await refreshTablolar();
      setDraft(buildTabloDraft(guncel));
      notify(200, t("notifications.tableChangesSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableChangesSaveFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function handleCreate(name: string, kolonlar: CreateKolonInput[], schemaId: number) {
    try {
      const created = await createTablo(name, kolonlar, schemaId);
      await refreshTablolar();
      setSelectedId(created.id);
      setShowCreateForm(false);
      notify(201, t("notifications.tableCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableCreateFailed"));
    }
  }

  async function handleCreateSchema(name: string) {
    try {
      const created = await createSchema(name);
      await refreshSchemalar();
      setShowCreateSchemaForm(false);
      notify(201, t("notifications.schemaCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.schemaCreateFailed"));
    }
  }

  async function handleRenameSchema(id: number, name: string) {
    try {
      await renameSchema(id, name);
      await refreshSchemalar();
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
    const tableIdsInSchema = new Set(
      tablolar.filter((tbl) => tbl.schemaId === id).map((tbl) => tbl.id)
    );
    setSchemalar((prev) => prev.filter((s) => s.id !== id));
    if (selectedId !== null && tableIdsInSchema.has(selectedId)) {
      setSelectedId(null);
    }

    const timerId = window.setTimeout(async () => {
      try {
        await deleteSchema(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.schemaDeleteFailed"));
      } finally {
        await refreshSchemalar();
        await refreshTablolar();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.schemaDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshSchemalar();
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
    setTablolar((prev) => prev.filter((tbl) => tbl.id !== id));
    if (selectedId === id) {
      setSelectedId(null);
    }

    const timerId = window.setTimeout(async () => {
      try {
        await deleteTablo(id);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.tableDeleteFailed"));
      } finally {
        await refreshTablolar();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.tableDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshTablolar();
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
      await changeTabloSchema(id, schemaId);
      await refreshTablolar();
      notify(200, t("notifications.tableSchemaChanged"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableSchemaChangeFailed"));
    }
  }

  async function handleCreateTag(name: string) {
    try {
      const created = await createTag(name);
      await refreshTags();
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

  async function handleCreateKullanici(kullaniciAdi: string, parola: string, rol: Rol) {
    try {
      const created = await createKullanici(kullaniciAdi, parola, rol);
      await refreshKullanicilar();
      notify(201, t("notifications.kullaniciCreated", { name: created.kullaniciAdi }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.kullaniciCreateFailed"));
    }
  }

  async function handleChangeKullaniciRol(id: number, rol: Rol) {
    try {
      await changeKullaniciRol(id, rol);
      await refreshKullanicilar();
      notify(200, t("notifications.kullaniciRolChanged"));
    } catch (err) {
      // Basarisiz olursa (ör. CONFLICT_LAST_ADMIN) listeyi YENIDEN CEKMIYORUZ — kullanicilar
      // state'i degismedigi icin <select> zaten backend'in kabul ettigi son degere geri doner,
      // ekstra bir "geri al" mantigi gerekmiyor.
      notifyFromError(notify, t, err, t("notifications.kullaniciRolChangeFailed"));
    }
  }

  async function handleDeleteKullanici(id: number) {
    try {
      await deleteKullanici(id);
      await refreshKullanicilar();
      notify(204, t("notifications.kullaniciDeleted"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.kullaniciDeleteFailed"));
    }
  }

  if (loading) {
    return <p className="loading-hint">{t("dashboard.loading")}</p>;
  }

  return (
    <div className="dashboard">
      <WorkspaceNav active={activeView} onChange={handleChangeActiveView} />

      {activeView === "tagler" && <TaglerPanel tags={tags} onLoadUsage={handleLoadTagUsage} />}

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
            tablolar={tablolar}
            selectedId={selectedId}
            onSelect={handleSelectTablo}
            onCreateClick={() => setShowCreateForm(true)}
            onCreateSchemaClick={() => setShowCreateSchemaForm(true)}
            onRenameSchema={handleRenameSchema}
            onDeleteSchema={handleDeleteSchema}
            onChangeTabloSchema={handleChangeTabloSchema}
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
            <section className="detail-panel empty-hint">{t("dashboard.selectTable")}</section>
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
