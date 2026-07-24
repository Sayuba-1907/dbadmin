import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  CreateKolonInput,
  Tablo,
  addKolon,
  changeKolonTag,
  createTablo,
  deleteKolon,
  deleteTablo,
  getTablolar,
  renameKolon,
  renameTablo,
} from "../api/tablolar";
import { Tag, createTag, getTags } from "../api/tags";
import { CreateTabloForm } from "../components/CreateTabloForm";
import { TabloDetail } from "../components/TabloDetail";
import { TabloSidebar } from "../components/TabloSidebar";
import {
  NOTIFICATION_DURATION_MS,
  notifyFromError,
  useNotify,
} from "../notifications/NotificationProvider";

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
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
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

  // Bos dependency array ([]) = sadece component ilk kez ekrana geldiginde (mount) bir kez
  // calisir, "sayfa acilinca ilk veriyi cek" anlaminda. exhaustive-deps kurali normalde
  // refreshTablolar/refreshTags/notify'i de listeye zorlar; onlar her render'da yeniden
  // olusturuldugu icin sonsuz donguye sokmemek adina bilerek kapatilmis.
  useEffect(() => {
    Promise.all([refreshTablolar(), refreshTags()])
      .catch((err) => notifyFromError(notify, t, err, t("notifications.loadFailed")))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleCreate(name: string, kolonlar: CreateKolonInput[]) {
    try {
      const created = await createTablo(name, kolonlar);
      await refreshTablolar();
      setSelectedId(created.id);
      setShowCreateForm(false);
      notify(201, t("notifications.tableCreated", { name: created.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableCreateFailed"));
    }
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

  async function handleRenameTablo(id: number, name: string) {
    try {
      await renameTablo(id, name);
      await refreshTablolar();
      notify(200, t("notifications.tableRenamed"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tableRenameFailed"));
    }
  }

  async function handleAddKolon(tabloId: number, input: CreateKolonInput) {
    try {
      await addKolon(tabloId, input);
      await refreshTablolar();
      notify(201, t("notifications.columnCreated", { name: input.name }));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.columnCreateFailed"));
    }
  }

  /** handleDeleteTablo ile ayni geri-alinabilir-silme deseni — bkz. oradaki aciklama. Burada sadece o tabloya ait kolonlar listesi filtreleniyor, tablonun kendisi degil. */
  function handleDeleteKolon(tabloId: number, kolonId: number) {
    setTablolar((prev) =>
      prev.map((tbl) =>
        tbl.id === tabloId
          ? { ...tbl, kolonlar: tbl.kolonlar.filter((k) => k.id !== kolonId) }
          : tbl
      )
    );

    const timerId = window.setTimeout(async () => {
      try {
        await deleteKolon(tabloId, kolonId);
      } catch (err) {
        notifyFromError(notify, t, err, t("notifications.columnDeleteFailed"));
      } finally {
        await refreshTablolar();
      }
    }, NOTIFICATION_DURATION_MS);

    notify(204, t("notifications.columnDeleted"), {
      label: t("common.undo"),
      onClick: () => {
        window.clearTimeout(timerId);
        refreshTablolar();
      },
    });
  }

  async function handleRenameKolon(tabloId: number, kolonId: number, name: string) {
    try {
      await renameKolon(tabloId, kolonId, name);
      await refreshTablolar();
      notify(200, t("notifications.columnRenamed"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.columnRenameFailed"));
    }
  }

  async function handleChangeKolonTag(tabloId: number, kolonId: number, tagId: number | null) {
    try {
      await changeKolonTag(tabloId, kolonId, tagId);
      await refreshTablolar();
      notify(200, t("notifications.tagOnColumnUpdated"));
    } catch (err) {
      notifyFromError(notify, t, err, t("notifications.tagUpdateFailed"));
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

  // selectedTablo ayri bir state degil — sadece selectedId'ye gore tablolar listesinden
  // turetiliyor (derived state). Boylece "secili tablo" ile "tablolar listesi" hicbir zaman
  // birbirinden senkronsuz kalmaz (refreshTablolar sonrasi otomatik gunceller).
  const selectedTablo = tablolar.find((tbl) => tbl.id === selectedId);

  if (loading) {
    return <p className="loading-hint">{t("dashboard.loading")}</p>;
  }

  return (
    <div className="dashboard">
      <TabloSidebar
        tablolar={tablolar}
        selectedId={selectedId}
        onSelect={setSelectedId}
        onCreateClick={() => setShowCreateForm(true)}
      />

      {selectedTablo ? (
        <TabloDetail
          tablo={selectedTablo}
          tags={tags}
          onDeleteTablo={handleDeleteTablo}
          onRenameTablo={handleRenameTablo}
          onAddKolon={handleAddKolon}
          onDeleteKolon={handleDeleteKolon}
          onRenameKolon={handleRenameKolon}
          onChangeKolonTag={handleChangeKolonTag}
          onCreateTag={handleCreateTag}
        />
      ) : (
        <section className="detail-panel empty-hint">{t("dashboard.selectTable")}</section>
      )}

      {showCreateForm && (
        <CreateTabloForm onSubmit={handleCreate} onClose={() => setShowCreateForm(false)} />
      )}
    </div>
  );
}
