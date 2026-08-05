import { DragEvent, FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { Tree } from "primereact/tree";
import { TreeNode } from "primereact/treenode";
import { InputText } from "primereact/inputtext";
import { Schema, TabloSummary } from "../api/schemas";
import { Kolon, Tablo } from "../api/tablolar";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../notifications/ConfirmProvider";

/**
 * Schema adindan deterministik bir renk turetir (ayni isim her zaman ayni renk) — sidebar'da
 * cok schema oldugunda goz taramasini kolaylastiran salt gorsel bir ipucu, backend'de karsiligi
 * yok. Basit bir string hash'i hue'ya (0-360) esliyor, sabit doygunluk/parlaklik koyu temada
 * hepsinin okunakli kalmasini sagliyor.
 */
function schemaColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash << 5) - hash + name.charCodeAt(i);
    hash |= 0;
  }
  const hue = Math.abs(hash) % 360;
  return `hsl(${hue}, 65%, 60%)`;
}

/** Tree'nin dugum verisinde (data alani) tasidigimiz ozel bilgi — schema mi, tablo mu, bir
 * tablonun altindaki tekil kolon mu, yoksa "bos" / "yukleniyor" bir yer tutucu satir mi
 * oldugunu ayirt eder. */
type NodeData =
  | { type: "schema"; schema: Schema }
  | { type: "tablo"; tablo: TabloSummary; schemaId: number }
  | { type: "kolon"; kolon: Kolon }
  | { type: "emptySchema" }
  | { type: "emptyTablo" }
  | { type: "loadingKolonlar" };

/**
 * Props: gercek veri (schemalar, her schema'nin tablo ozetleri, selectedId) ve "bir seye
 * tikladiginda ne olsun" callback'leri hep Dashboard'dan gelir. Bu component'in kendi tuttugu
 * state, arama kutusunun metni, hangi schema'larin acik (expanded) oldugu ve hangi schema su an
 * yeniden adlandirma modunda oldugu — hepsi salt gorsel/gecici UI durumu, Dashboard'un ya da
 * backend'in hic umrunda degil.
 * <p>
 * Her schema'nin altindaki tablo listesi TabloSummary (sadece id/name/kolonSayisi) — tablonun
 * kolonlarinin tam listesi burada YOK. Bir tabloya TIKLAMAK (secmek) onu sag paneldeki
 * TabloDetail icin ceker (Dashboard.selectTablo); bir tabloyu agacta GENISLETMEK ise ayrica,
 * lazy olarak {@link onLoadKolonlar} ile ceker ve {@code kolonlarByTabloId}'de onbelleklenir —
 * ikisi ayni veriyi farkli amaclarla iki kere isteyebilir (basitlik icin bilerek boyle, TaglerPanel'in
 * kullanim onbellegiyle ayni desen).
 */
interface TabloSidebarProps {
  schemalar: Schema[];
  tabloSummariesBySchema: Record<number, TabloSummary[]>;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreateClick: () => void;
  onCreateSchemaClick: () => void;
  onRenameSchema: (id: number, name: string) => Promise<void>;
  onDeleteSchema: (id: number) => void;
  onChangeTabloSchema: (id: number, schemaId: number) => Promise<void>;
  onLoadKolonlar: (tabloId: number) => Promise<Tablo>;
}

/**
 * Sol tarafta schema -> tablo hiyerarsisi + "yeni tablo"/"yeni schema" butonlari + arama kutusu.
 * <p>
 * PrimeReact'in {@link Tree} bileseni SADECE acilir/kapanir iskelet + klavye/erisilebilirlik
 * mekanigi icin kullaniliyor — surukle-birak, yeniden adlandirma formu, renk noktasi gibi her
 * sey hala bizim kendi {@code nodeTemplate}'imizde, kendi state'imizle yonetiliyor (Tree'nin
 * kendi {@code dragdropScope} DnD'sine BILEREK guvenilmedi: bizim "tabloyu schema'ya tasi"
 * semantigimiz Tree'nin "dugumu yeniden sirala/tasi" modeliyle birebir ortusmuyor).
 */
export function TabloSidebar({
  schemalar,
  tabloSummariesBySchema,
  selectedId,
  onSelect,
  onCreateClick,
  onCreateSchemaClick,
  onRenameSchema,
  onDeleteSchema,
  onChangeTabloSchema,
  onLoadKolonlar,
}: TabloSidebarProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  const confirm = useConfirm();
  const [query, setQuery] = useState("");
  const [expandedSchemaIds, setExpandedSchemaIds] = useState<Set<number>>(new Set());
  const [editingSchemaId, setEditingSchemaId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");
  // Agacta bir tablo genisletilince (bkz. handleToggle) kolonlari lazy cekilir ve burada
  // onbelleklenir — ayni tabloyu tekrar ac/kapa yapmak ikinci bir istek atmaz (TaglerPanel'in
  // kullanim onbellegiyle ayni desen).
  const [expandedTabloIds, setExpandedTabloIds] = useState<Set<number>>(new Set());
  const [kolonlarByTabloId, setKolonlarByTabloId] = useState<Map<number, Kolon[]>>(new Map());
  const [loadingTabloIds, setLoadingTabloIds] = useState<Set<number>>(new Set());
  // Surukle-birak sirasinda hangi tablo hangi schema'dan tasiniyor ve hangi schema basligi
  // hedef olarak vurgulanmali — hepsi salt gorsel/gecici state, backend'in umrunde degil.
  // draggedFromSchemaId ayrica tutuluyor cunku TabloSummary'de schemaId alani yok (sadece
  // id/name/kolonSayisi) — tablonun su an hangi schema'da oldugunu sadece hangi listeden
  // suruklendiginden biliyoruz.
  const [draggedTabloId, setDraggedTabloId] = useState<number | null>(null);
  const [draggedFromSchemaId, setDraggedFromSchemaId] = useState<number | null>(null);
  const [dragOverSchemaId, setDragOverSchemaId] = useState<number | null>(null);
  // Her schema'nin altindaki tablo listesinin hangi kritere gore siralanacagi — sadece
  // gorsel/gecici UI durumu, backend'e hic gitmiyor (backend zaten isme gore sirali doner,
  // "kolon sayisi" secenegi tamamen frontend'de, elimizdeki ozet veri uzerinde hesaplaniyor).
  const [sortBy, setSortBy] = useState<"name" | "kolonCount">("name");

  /** Bir tablo, uzerine birakildigi schema'nin basligina surukle-birakla tasinir. Zaten o schema'daysa hicbir sey yapmaz. */
  function handleDropOnSchema(event: DragEvent, targetSchemaId: number) {
    event.preventDefault();
    setDragOverSchemaId(null);
    const tabloId = draggedTabloId;
    const fromSchemaId = draggedFromSchemaId;
    setDraggedTabloId(null);
    setDraggedFromSchemaId(null);
    if (tabloId == null) {
      return;
    }
    if (fromSchemaId !== targetSchemaId) {
      onChangeTabloSchema(tabloId, targetSchemaId);
    }
  }

  async function handleRenameSubmit(event: FormEvent, schemaId: number) {
    event.preventDefault();
    await onRenameSchema(schemaId, renameDraft);
    setEditingSchemaId(null);
  }

  // Buyuk/kucuk harf duyarsiz, basit bir "icinde geciyor mu" filtresi — backend'e hic gitmiyor,
  // tamamen zaten ekranda olan (yuklenmis) ozet listelerin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const isSearching = normalizedQuery !== "";

  function sortedTablolarOf(schemaId: number): TabloSummary[] {
    const all = tabloSummariesBySchema[schemaId] ?? [];
    const filtered = isSearching
      ? all.filter((tablo) => tablo.name.toLowerCase().includes(normalizedQuery))
      : all;
    // "kolonCount" -> en cok kolonlu tablo en ustte (azalan, b - a).
    // "name" -> Turkce alfabetik siraya gore (localeCompare "tr").
    return [...filtered].sort((a, b) =>
      sortBy === "kolonCount" ? b.kolonSayisi - a.kolonSayisi : a.name.localeCompare(b.name, "tr")
    );
  }

  const hasAnyTablo = Object.values(tabloSummariesBySchema).some((list) => list.length > 0);

  // Arama yaparken bir schema'nin altinda hic eslesme yoksa o schema'yi tamamen gizliyoruz;
  // aramiyorsak (bos schema dahil) her schema gorunur — kullanici yeni actigi bos bir schema'yi
  // "kayboldu" sanmasin diye.
  const visibleSchemalar = isSearching
    ? schemalar.filter((schema) => sortedTablolarOf(schema.id).length > 0)
    : schemalar;

  /** Bir tablo dugumunun altindaki kolon dugumlerini kurar — henuz cekilmemisse bos (Tree
   * "children yok" sanip yaprak gibi davranmaz, cunku tablo dugumunun kendisi leaf:false). */
  function tabloChildren(tabloId: number): TreeNode[] {
    if (loadingTabloIds.has(tabloId)) {
      return [
        {
          key: `tablo-${tabloId}-loading`,
          data: { type: "loadingKolonlar" } satisfies NodeData,
          leaf: true,
        },
      ];
    }
    const kolonlar = kolonlarByTabloId.get(tabloId);
    if (!kolonlar) {
      return [];
    }
    if (kolonlar.length === 0) {
      return [
        {
          key: `tablo-${tabloId}-empty`,
          data: { type: "emptyTablo" } satisfies NodeData,
          leaf: true,
        },
      ];
    }
    return kolonlar.map((kolon) => ({
      key: `kolon-${kolon.id}`,
      data: { type: "kolon", kolon } satisfies NodeData,
      leaf: true,
    }));
  }

  const nodes: TreeNode[] = visibleSchemalar.map((schema) => {
    const schemaTablolar = sortedTablolarOf(schema.id);
    const children: TreeNode[] =
      schemaTablolar.length > 0
        ? schemaTablolar.map((tablo) => ({
            key: `tablo-${tablo.id}`,
            data: { type: "tablo", tablo, schemaId: schema.id } satisfies NodeData,
            leaf: false,
            children: tabloChildren(tablo.id),
          }))
        : [
            {
              key: `empty-${schema.id}`,
              data: { type: "emptySchema" } satisfies NodeData,
              leaf: true,
            },
          ];
    return {
      key: `schema-${schema.id}`,
      data: { type: "schema", schema } satisfies NodeData,
      children,
    };
  });

  // Aramada eslesen her schema otomatik acik gosterilir (eski davranisla ayni — bkz.
  // orijinal "expanded = isSearching || expandedSchemaIds.has(...)"), Tree'nin kendi
  // expandedKeys'i bu kaynaklari birlestirerek olusturuluyor.
  const expandedKeys: Record<string, boolean> = {};
  visibleSchemalar.forEach((schema) => {
    if (isSearching || expandedSchemaIds.has(schema.id)) {
      expandedKeys[`schema-${schema.id}`] = true;
    }
  });
  expandedTabloIds.forEach((id) => {
    expandedKeys[`tablo-${id}`] = true;
  });

  async function loadKolonlarFor(tabloId: number) {
    setLoadingTabloIds((prev) => new Set(prev).add(tabloId));
    try {
      const tablo = await onLoadKolonlar(tabloId);
      setKolonlarByTabloId((prev) => new Map(prev).set(tabloId, tablo.kolonlar));
    } finally {
      setLoadingTabloIds((prev) => {
        const next = new Set(prev);
        next.delete(tabloId);
        return next;
      });
    }
  }

  function handleToggle(value: Record<string, boolean>) {
    const nextSchemaIds = new Set<number>();
    const nextTabloIds = new Set<number>();
    Object.keys(value).forEach((key) => {
      if (!value[key]) {
        return;
      }
      if (key.startsWith("schema-")) {
        nextSchemaIds.add(Number(key.slice("schema-".length)));
      } else if (key.startsWith("tablo-")) {
        nextTabloIds.add(Number(key.slice("tablo-".length)));
      }
    });
    // Yeni acilan (onceden acik olmayan, henuz onbellekte olmayan) her tablo icin kolonlari cek.
    nextTabloIds.forEach((tabloId) => {
      if (!expandedTabloIds.has(tabloId) && !kolonlarByTabloId.has(tabloId)) {
        loadKolonlarFor(tabloId);
      }
    });
    setExpandedSchemaIds(nextSchemaIds);
    setExpandedTabloIds(nextTabloIds);
  }

  /** Sadece kucuk ok'a degil, schema baslik satirinin tamamina (isim/renk noktasi/sayi alanina)
   * tiklayinca da ac/kapa — Tree'nin kendi toggler butonu Edit/Delete'ten ayri, kucuk ve tek
   * basina tiklamasi mantiksiz kaliyordu. */
  function toggleSchema(schemaId: number) {
    setExpandedSchemaIds((prev) => {
      const next = new Set(prev);
      if (next.has(schemaId)) {
        next.delete(schemaId);
      } else {
        next.add(schemaId);
      }
      return next;
    });
  }

  return (
    <aside className="sidebar fadeinup animation-duration-200">
      {canWrite && (
        <div className="sidebar-actions">
          <button className="btn btn-primary" onClick={onCreateClick}>
            {t("sidebar.newTable")}
          </button>
          <button className="btn" onClick={onCreateSchemaClick}>
            {t("sidebar.newSchema")}
          </button>
        </div>
      )}
      {hasAnyTablo && (
        <div className="sidebar-search-row">
          <InputText
            type="text"
            className="sidebar-search block"
            placeholder={t("sidebar.searchPlaceholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="sidebar-sort-control relative" title={t("sidebar.sortLabel")}>
            <span className="sidebar-sort-icon absolute" aria-hidden="true">
              &#8645;
            </span>
            <select
              className="sidebar-sort cursor-pointer"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as "name" | "kolonCount")}
              aria-label={t("sidebar.sortLabel")}
            >
              <option value="name">{t("sidebar.sortByName")}</option>
              <option value="kolonCount">{t("sidebar.sortByKolonCount")}</option>
            </select>
          </div>
        </div>
      )}
      {schemalar.length === 0 && <p className="empty-hint">{t("sidebar.empty")}</p>}
      {schemalar.length > 0 && isSearching && visibleSchemalar.length === 0 && (
        <p className="empty-hint">{t("sidebar.noSearchResults")}</p>
      )}
      {visibleSchemalar.length > 0 && (
        <Tree
          value={nodes}
          expandedKeys={expandedKeys}
          onToggle={(e) => handleToggle(e.value as Record<string, boolean>)}
          className="schema-tree"
          nodeTemplate={(node) => {
            const data = node.data as NodeData;

            if (data.type === "emptySchema") {
              return <span className="empty-hint">{t("sidebar.noTablesInSchema")}</span>;
            }

            if (data.type === "emptyTablo") {
              return <span className="empty-hint">{t("sidebar.noKolonlarInTablo")}</span>;
            }

            if (data.type === "loadingKolonlar") {
              return (
                <div
                  role="status"
                  aria-busy="true"
                  aria-label={t("sidebar.loadingKolonlar")}
                  className="sidebar-kolon-loading"
                >
                  <div className="skeleton skeleton-block" style={{ height: 12, width: "60%" }} />
                  <div className="skeleton skeleton-block" style={{ height: 12, width: "45%" }} />
                </div>
              );
            }

            if (data.type === "kolon") {
              const kolon = data.kolon;
              return (
                <span className="sidebar-kolon-item w-full">
                  <span className="mono overflow-hidden text-overflow-ellipsis white-space-nowrap">
                    {kolon.name}
                  </span>
                  <span className={`type-badge type-badge-${kolon.type}`}>{kolon.type}</span>
                  {kolon.primaryKey && <span className="pk-badge">PK</span>}
                </span>
              );
            }

            if (data.type === "tablo") {
              const tablo = data.tablo;
              return (
                <button
                  className={`tablo-list-item w-full text-left${tablo.id === selectedId ? " selected" : ""}${
                    draggedTabloId === tablo.id ? " dragging" : ""
                  }`}
                  onClick={() => onSelect(tablo.id)}
                  draggable={canWrite}
                  onDragStart={(e) => {
                    // Tree'nin kendi dugumu (li) de bir onDragStart dinleyicisi tasiyor
                    // (dragdropScope hic verilmese bile kosulsuz calisiyor, kaynagina bakinca
                    // event.dataTransfer.setData(...) cagiriyor) — stopPropagation olmadan bu
                    // hem jsdom testlerinde dataTransfer tanimsiz oldugu icin hata firlatiyor
                    // hem de gercek tarayicida bizim surukleme akisimizla alakasiz veri yaziyor.
                    e.stopPropagation();
                    setDraggedTabloId(tablo.id);
                    setDraggedFromSchemaId(data.schemaId);
                    // Bazi ortamlarda (ör. jsdom testleri) dataTransfer tanimsiz olabiliyor —
                    // gercek tarayicida her zaman dolu geliyor ama yine de koruyoruz.
                    if (e.dataTransfer) {
                      e.dataTransfer.effectAllowed = "move";
                    }
                  }}
                  onDragEnd={() => {
                    setDraggedTabloId(null);
                    setDraggedFromSchemaId(null);
                  }}
                  title={t("sidebar.dragToMoveHint")}
                >
                  <span className="tablo-name overflow-hidden text-overflow-ellipsis white-space-nowrap">
                    {tablo.name}
                  </span>
                  <span className="kolon-count">{tablo.kolonSayisi}</span>
                </button>
              );
            }

            const schema = data.schema;
            const isEditing = editingSchemaId === schema.id;

            if (isEditing) {
              return (
                <form
                  className="inline-edit-form inline-flex"
                  onSubmit={(e) => handleRenameSubmit(e, schema.id)}
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
                    onClick={() => setEditingSchemaId(null)}
                  >
                    {t("common.cancel")}
                  </button>
                </form>
              );
            }

            return (
              <div
                className={`schema-header-row${dragOverSchemaId === schema.id ? " drag-over" : ""}`}
                // Drop hedefini schema baslik satirinin tamamina yayiyoruz — eskiden tum
                // schema hucresi (baslik + acikken altindaki tablo listesi) hedefti, Tree'nin
                // dugum yapisinda bize ait olan tek alan bu baslik satiri, o yuzden kapsam
                // biraz daraldi ama hala "tablo adinin tam ustune" gerekmiyor.
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragOverSchemaId(schema.id);
                }}
                onDragLeave={() =>
                  setDragOverSchemaId((prev) => (prev === schema.id ? null : prev))
                }
                onDrop={(e) => handleDropOnSchema(e, schema.id)}
              >
                <span
                  className="schema-header cursor-pointer"
                  role="button"
                  tabIndex={0}
                  onClick={() => toggleSchema(schema.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      toggleSchema(schema.id);
                    }
                  }}
                >
                  <span
                    className="schema-dot"
                    style={{ backgroundColor: schemaColor(schema.name) }}
                    aria-hidden="true"
                  />
                  <span className="mono overflow-hidden text-overflow-ellipsis white-space-nowrap">
                    {schema.name}
                  </span>
                  <span className="kolon-count">{schema.tabloSayisi}</span>
                </span>
                {canWrite && (
                  <button
                    className="btn btn-link"
                    onClick={() => {
                      setRenameDraft(schema.name);
                      setEditingSchemaId(schema.id);
                    }}
                  >
                    {t("common.edit")}
                  </button>
                )}
                {canWrite && (
                  <button
                    className="btn btn-link btn-danger"
                    onClick={async () => {
                      if (
                        await confirm(
                          t("sidebar.confirmDeleteSchema", {
                            name: schema.name,
                            count: schema.tabloSayisi,
                          })
                        )
                      ) {
                        onDeleteSchema(schema.id);
                      }
                    }}
                  >
                    {t("common.delete")}
                  </button>
                )}
              </div>
            );
          }}
        />
      )}
    </aside>
  );
}
