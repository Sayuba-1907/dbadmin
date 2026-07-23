import { useEffect, useState } from "react";
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
import { notifyFromError, useNotify } from "../notifications/NotificationProvider";

export function Dashboard() {
  const [tablolar, setTablolar] = useState<Tablo[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const notify = useNotify();

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

  useEffect(() => {
    Promise.all([refreshTablolar(), refreshTags()])
      .catch((err) => notifyFromError(notify, err, "Veriler yuklenemedi"))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleCreate(name: string, kolonlar: CreateKolonInput[]) {
    try {
      const created = await createTablo(name, kolonlar);
      await refreshTablolar();
      setSelectedId(created.id);
      setShowCreateForm(false);
      notify(201, `"${created.name}" tablosu olusturuldu`);
    } catch (err) {
      notifyFromError(notify, err, "Tablo olusturulamadi");
    }
  }

  async function handleDeleteTablo(id: number) {
    try {
      await deleteTablo(id);
      await refreshTablolar();
      if (selectedId === id) {
        setSelectedId(null);
      }
      notify(204, "Tablo silindi");
    } catch (err) {
      notifyFromError(notify, err, "Tablo silinemedi");
    }
  }

  async function handleRenameTablo(id: number, name: string) {
    try {
      await renameTablo(id, name);
      await refreshTablolar();
      notify(200, "Tablo yeniden adlandirildi");
    } catch (err) {
      notifyFromError(notify, err, "Tablo yeniden adlandirilamadi");
    }
  }

  async function handleAddKolon(tabloId: number, input: CreateKolonInput) {
    try {
      await addKolon(tabloId, input);
      await refreshTablolar();
      notify(201, `"${input.name}" kolonu eklendi`);
    } catch (err) {
      notifyFromError(notify, err, "Kolon eklenemedi");
    }
  }

  async function handleDeleteKolon(tabloId: number, kolonId: number) {
    try {
      await deleteKolon(tabloId, kolonId);
      await refreshTablolar();
      notify(204, "Kolon silindi");
    } catch (err) {
      notifyFromError(notify, err, "Kolon silinemedi");
    }
  }

  async function handleRenameKolon(tabloId: number, kolonId: number, name: string) {
    try {
      await renameKolon(tabloId, kolonId, name);
      await refreshTablolar();
      notify(200, "Kolon yeniden adlandirildi");
    } catch (err) {
      notifyFromError(notify, err, "Kolon yeniden adlandirilamadi");
    }
  }

  async function handleChangeKolonTag(tabloId: number, kolonId: number, tagId: number | null) {
    try {
      await changeKolonTag(tabloId, kolonId, tagId);
      await refreshTablolar();
      notify(200, "Kolonun tag'i guncellendi");
    } catch (err) {
      notifyFromError(notify, err, "Tag guncellenemedi");
    }
  }

  async function handleCreateTag(name: string) {
    try {
      const created = await createTag(name);
      await refreshTags();
      notify(201, `"${created.name}" tag'i olusturuldu`);
    } catch (err) {
      notifyFromError(notify, err, "Tag olusturulamadi");
    }
  }

  const selectedTablo = tablolar.find((t) => t.id === selectedId);

  if (loading) {
    return <p className="loading-hint">Yukleniyor...</p>;
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
        <section className="detail-panel empty-hint">Bir tablo sec</section>
      )}

      {showCreateForm && (
        <CreateTabloForm onSubmit={handleCreate} onClose={() => setShowCreateForm(false)} />
      )}
    </div>
  );
}
