import { FormEvent, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Paginator, PaginatorPageChangeEvent } from "primereact/paginator";
import { InputText } from "primereact/inputtext";
import { Button } from "primereact/button";
import { Checkbox } from "primereact/checkbox";
import { DraftColumn, COLUMN_TYPES, ColumnType, TableDraft } from "../api/tables";
import {
  TABLE_DATA_PAGE_SIZES,
  TableDataPageSize,
  TableDataResult,
  exportTableDataCsv,
  getTableData,
  insertTableRow,
  updateTableRow,
} from "../api/tableData";
import { Tag } from "../api/tags";
import { tagColorStyle } from "../utils/tagColor";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";
import { useAuth } from "../auth/AuthProvider";
import { useConfirm } from "../notifications/ConfirmProvider";
import { notifyFromError, useNotify } from "../notifications/NotificationProvider";

type DetailTab = "columns" | "data";

/**
 * Handler'lar Dashboard'dan geliyor. Buradaki tum onChange* callback'leri SENKRON — hicbiri
 * API'ye gitmez, hepsi Dashboard'daki taslagi (draft) gunceller. Tek asenkron olan {@code onSave}:
 * o da taslak ile orijinal tablo arasindaki farki TEK istekte gonderir (bkz.
 * Dashboard.handleSaveDraft / TabloService.applyChanges).
 */
interface TableDetailProps {
  draft: TableDraft;
  tags: Tag[];
  isDirty: boolean;
  saving: boolean;
  /** Sürüklenerek bekleyen bir schema taşıması varsa hedef schema'nın adı, yoksa null. */
  pendingSchemaName: string | null;
  onChangeName: (name: string) => void;
  onAddColumn: (input: {
    name: string;
    type: ColumnType;
    tagId: number | null;
    primaryKey: boolean;
  }) => void;
  onToggleDeleteKolon: (columnId: number) => void;
  onChangeColumnName: (columnId: number, name: string) => void;
  onChangeColumnTag: (columnId: number, tagId: number | null) => void;
  onChangeColumnPrimaryKey: (columnId: number, primaryKey: boolean) => void;
  onSave: () => Promise<void>;
  onDiscard: () => void;
  onDeleteTablo: (id: number) => void;
  onCreateTag: (name: string) => Promise<void>;
}

/**
 * Secili tablonun detay paneli: baslik (duzenlenebilir isim), column tablosu, "column ekle" ve
 * "tag olustur" formlari hepsi burada. "Tabloyu sil" HARIC hicbir aksiyon aninda API'ye gitmez
 * — hepsi {@code draft}'i gunceller, en sonda "Kaydet"e basinca Dashboard tek bir istekte
 * gonderir. Bu yuzden butun input'lar dogrudan {@code draft.*}'a baglidir (kontrollu/controlled),
 * kendi ayri bir "duzenleme modu" tutmazlar.
 */
export function TableDetail({
  draft,
  tags,
  isDirty,
  saving,
  pendingSchemaName,
  onChangeName,
  onAddColumn,
  onToggleDeleteKolon,
  onChangeColumnName,
  onChangeColumnTag,
  onChangeColumnPrimaryKey,
  onSave,
  onDiscard,
  onDeleteTablo,
  onCreateTag,
}: TableDetailProps) {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
  // Satir duzenleme PRIMARY KEY ile satiri bulur (bkz. TableDataService#updateRow) — PK'siz
  // bir tabloda "Duzenle" aksiyonu hic gosterilmiyor, backend'in 400'unu kullaniciya gostermek
  // yerine imkansiz olan islemi bastan sakliyoruz.
  const hasPrimaryKey = draft.columns.some((column) => column.primaryKey);
  const confirm = useConfirm();
  const notify = useNotify();
  const [columnName, setKolonName] = useState("");
  const [columnType, setKolonType] = useState<ColumnType>(COLUMN_TYPES[0]);
  const [columnPrimaryKey, setKolonPrimaryKey] = useState(false);
  const [newTagName, setNewTagName] = useState("");
  // Column tablosunda ad'a gore arama — DataTable'in globalFilter'i, onceden hic yoktu (deneme:
  // PrimeReact DataTable, bkz. DECISIONS.md).
  const [columnSearch, setColumnSearch] = useState("");

  // "Columns" (mevcut sema duzenleyici) / "Data" (requirement notu 7 — DBeaver'daki gibi gercek
  // satirlari goster) sekmeleri arasinda gecis — draft'in tersine bu SALT OKUNUR bir gorunum,
  // hicbir taslak/kaydet mekanizmasina girmez.
  const [activeTab, setActiveTab] = useState<DetailTab>("columns");
  const [dataPage, setDataPage] = useState(0);
  const [dataPageSize, setDataPageSize] = useState<TableDataPageSize>(20);
  const [tableData, setTableData] = useState<TableDataResult | null>(null);
  const [loadingData, setLoadingData] = useState(false);
  // dataPage AYNI kalsa bile (ör. zaten son sayfadaysak ve oraya bir satir daha eklendiyse)
  // yeniden cekmeyi zorlamak icin — useEffect'in bagimlilik listesindeki tek "hicbir zaman ayni
  // kalmayan" deger budur.
  const [dataRefreshKey, setDataRefreshKey] = useState(0);
  const [addRowOpen, setAddRowOpen] = useState(false);
  const [newRowValues, setNewRowValues] = useState<Record<string, string>>({});
  const [addingRow, setAddingRow] = useState(false);
  const [exportingCsv, setExportingCsv] = useState(false);
  const [editingRow, setEditingRow] = useState<Record<string, unknown> | null>(null);
  const [editRowValues, setEditRowValues] = useState<Record<string, string>>({});
  const [savingEditRow, setSavingEditRow] = useState(false);

  // Farkli bir tabloya gecildiginde (draft.tableId degisince) onceki tablonun sayfasi/verisi
  // gosterilmeye devam etmesin diye sifirlaniyor — "Data" sekmesi acik kalsa bile.
  useEffect(() => {
    setDataPage(0);
    setTableData(null);
    setAddRowOpen(false);
    setNewRowValues({});
    setEditingRow(null);
    setEditRowValues({});
  }, [draft.tableId]);

  useEffect(() => {
    if (activeTab !== "data") {
      return;
    }
    let cancelled = false;
    setLoadingData(true);
    getTableData(draft.tableId, dataPage, dataPageSize)
      .then((result) => {
        if (!cancelled) {
          setTableData(result);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          notifyFromError(notify, t, err, t("tabloDetail.dataLoadFailed"));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingData(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [activeTab, draft.tableId, dataPage, dataPageSize, dataRefreshKey, notify, t]);

  /**
   * Kolon tipine gore ham metin girdisini backend'in bekledigi degere cevirir. Bos birakilan
   * bir alan values map'ine HIC katilmiyor (bkz. cagiran) — "bu kolona dokunma" anlamina gelir,
   * checkbox ise (bos birakilamayacagi icin) her zaman katiliyor.
   */
  function parseNewRowValue(column: DraftColumn, raw: string): unknown {
    if (column.type === "boolean") {
      return raw === "true";
    }
    if (column.type === "numeric") {
      return raw === "" ? undefined : Number(raw);
    }
    return raw === "" ? undefined : raw;
  }

  async function handleInsertRowSubmit(event: FormEvent) {
    event.preventDefault();
    const values: Record<string, unknown> = {};
    for (const column of draft.columns) {
      const raw = newRowValues[column.name] ?? (column.type === "boolean" ? "false" : "");
      const parsed = parseNewRowValue(column, raw);
      if (parsed !== undefined) {
        values[column.name] = parsed;
      }
    }
    if (Object.keys(values).length === 0) {
      notify(400, t("tabloDetail.dataAddRowEmpty"));
      return;
    }
    setAddingRow(true);
    try {
      await insertTableRow(draft.tableId, values);
      notify(204, t("tabloDetail.dataAddRowSaved"));
      setNewRowValues({});
      // Yeni satir PRIMARY KEY'e gore sirali sayfalamada (bkz. TableDataService#orderByClause)
      // hep son sayfada cikar — kullaniciyi oraya goturuyoruz, aksi halde "ekledim ama
      // gormuyorum" hissi yaratirdi.
      const newTotal = (tableData?.totalRows ?? 0) + 1;
      const lastPage = Math.max(0, Math.ceil(newTotal / dataPageSize) - 1);
      setDataPage(lastPage);
      setDataRefreshKey((key) => key + 1);
    } catch (err) {
      notifyFromError(notify, t, err, t("tabloDetail.dataAddRowFailed"));
    } finally {
      setAddingRow(false);
    }
  }

  /**
   * Backend'in dondugu "2021-04-01 00:00:00.0" bicimini datetime-local input'unun bekledigi
   * "2021-04-01T00:00" bicimine cevirir — sadece prefill icin, gonderirken (T'li haliyle)
   * Postgres zaten kabul ediyor (bkz. parseNewRowValue).
   */
  function formatDatetimeForInput(raw: string): string {
    const match = raw.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/);
    return match ? `${match[1]}T${match[2]}` : "";
  }

  /**
   * Bir satirin "Duzenle" butonuna basilinca cagrilir — PK disindaki kolonlari duzenleme
   * formuna doldurur (PK, satirin kimligini WHERE'de belirledigi icin duzenlenemez, bkz.
   * TableDataService#updateRow). Ayni anda acik olan Add Row formu varsa kapatilir.
   */
  function startEditRow(row: Record<string, unknown>) {
    setAddRowOpen(false);
    setEditingRow(row);
    const initial: Record<string, string> = {};
    for (const column of draft.columns) {
      if (column.primaryKey) {
        continue;
      }
      const value = row[column.name];
      if (value === null || value === undefined) {
        initial[column.name] = "";
      } else if (column.type === "datetime") {
        initial[column.name] = formatDatetimeForInput(String(value));
      } else {
        initial[column.name] = String(value);
      }
    }
    setEditRowValues(initial);
  }

  async function handleUpdateRowSubmit(event: FormEvent) {
    event.preventDefault();
    if (!editingRow) {
      return;
    }
    const pk: Record<string, unknown> = {};
    for (const column of draft.columns) {
      if (column.primaryKey) {
        pk[column.name] = editingRow[column.name];
      }
    }
    const values: Record<string, unknown> = {};
    for (const column of draft.columns) {
      if (column.primaryKey) {
        continue;
      }
      const raw = editRowValues[column.name] ?? (column.type === "boolean" ? "false" : "");
      const parsed = parseNewRowValue(column, raw);
      if (parsed !== undefined) {
        values[column.name] = parsed;
      }
    }
    if (Object.keys(values).length === 0) {
      notify(400, t("tabloDetail.dataAddRowEmpty"));
      return;
    }
    setSavingEditRow(true);
    try {
      await updateTableRow(draft.tableId, pk, values);
      notify(204, t("tabloDetail.dataEditRowSaved"));
      setEditingRow(null);
      setEditRowValues({});
      setDataRefreshKey((key) => key + 1);
    } catch (err) {
      notifyFromError(notify, t, err, t("tabloDetail.dataEditRowFailed"));
    } finally {
      setSavingEditRow(false);
    }
  }

  /**
   * Requirement notu 8 ("CSV Export ekle -> minio'ya yazilacak"). Backend dosyayi hem MinIO'ya
   * yazar hem de ayni yanitla geri doner — burada sadece tarayici indirmesini tetikliyoruz.
   */
  async function handleExportCsv() {
    setExportingCsv(true);
    try {
      await exportTableDataCsv(draft.tableId, `${draft.name}.csv`);
    } catch (err) {
      notifyFromError(notify, t, err, t("tabloDetail.dataExportCsvFailed"));
    } finally {
      setExportingCsv(false);
    }
  }

  function handleAddKolonSubmit(event: FormEvent) {
    event.preventDefault();
    onAddColumn({ name: columnName, type: columnType, tagId: null, primaryKey: columnPrimaryKey });
    setKolonName("");
    setKolonPrimaryKey(false);
  }

  async function handleCreateTagSubmit(event: FormEvent) {
    event.preventDefault();
    await onCreateTag(newTagName);
    setNewTagName("");
  }

  return (
    <section className="detail-panel fadeinup animation-duration-200">
      {/* VIEWER rolu icin tum yazma kontrollerini tek yerden kapatir — fieldset'in disabled'i
          DOM derinligi fark etmeksizin her input/select/button torununa uygulanir, yani
          DataTable'in column body'lerindeki tekil kontrollere ayrica dokunmaya gerek yok. Backend zaten
          403 donuyor (bkz. SecurityConfig); bu sadece kullanicinin yapamayacagi bir seyi
          denemesini engelleyen bir UX katmani. */}
      <fieldset className="unstyled-fieldset" disabled={!canWrite}>
        <div className="detail-header flex align-items-center">
          <input
            type="text"
            className="table-name-input"
            value={draft.name}
            onChange={(e) => onChangeName(e.target.value)}
          />
          {pendingSchemaName && (
            <span className="pending-move-hint">
              {t("tabloDetail.pendingSchemaMove", { schema: pendingSchemaName })}
            </span>
          )}
          <div className="detail-header-actions flex ml-auto">
            {isDirty && (
              <>
                <button className="btn btn-link" onClick={onDiscard} disabled={saving}>
                  {t("common.cancel")}
                </button>
                <button className="btn btn-primary" onClick={onSave} disabled={saving}>
                  {t("common.save")}
                </button>
              </>
            )}
            {canWrite && (
              <button
                className="btn btn-danger"
                onClick={async () => {
                  if (await confirm(t("tabloDetail.confirmDeleteTable", { name: draft.name }))) {
                    onDeleteTablo(draft.tableId);
                  }
                }}
              >
                {t("tabloDetail.deleteTable")}
              </button>
            )}
          </div>
        </div>

        <div className="tab-bar" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "columns"}
            className={`tab-button${activeTab === "columns" ? " tab-button-active" : ""}`}
            onClick={() => setActiveTab("columns")}
          >
            {t("tabloDetail.tabColumns")}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === "data"}
            className={`tab-button${activeTab === "data" ? " tab-button-active" : ""}`}
            onClick={() => setActiveTab("data")}
          >
            {t("tabloDetail.tabData")}
          </button>
        </div>

        {activeTab === "columns" && (
          <div className="detail-card">
            {draft.columns.length > 0 && (
              <input
                type="text"
                className="sidebar-search block"
                style={{ marginBottom: "var(--space-2)", maxWidth: 240 }}
                placeholder={t("tabloDetail.columnSearchPlaceholder")}
                value={columnSearch}
                onChange={(e) => setColumnSearch(e.target.value)}
              />
            )}
            <DataTable
              value={draft.columns}
              dataKey="id"
              className="column-table w-full"
              emptyMessage={t("tabloDetail.emptyColumns")}
              globalFilter={columnSearch}
              globalFilterFields={["name"]}
              rowClassName={(column: DraftColumn) =>
                column.toDelete ? "column-row-to-delete" : undefined
              }
            >
              <Column
                field="name"
                header={t("tabloDetail.colName")}
                sortable
                body={(column: DraftColumn) => (
                  <>
                    <input
                      type="text"
                      className="mono"
                      value={column.name}
                      onChange={(e) => onChangeColumnName(column.id, e.target.value)}
                      disabled={column.toDelete}
                    />
                    {column.isNew && (
                      <span className="new-badge">{t("tabloDetail.newColumnBadge")}</span>
                    )}
                  </>
                )}
              />
              <Column
                field="type"
                header={t("tabloDetail.colType")}
                sortable
                body={(column: DraftColumn) => (
                  <span className={`type-badge type-badge-${column.type}`}>{column.type}</span>
                )}
              />
              <Column
                header={t("tabloDetail.colPrimaryKey")}
                body={(column: DraftColumn) => (
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={column.primaryKey}
                      disabled={column.toDelete}
                      onChange={(e) => onChangeColumnPrimaryKey(column.id, e.target.checked)}
                      aria-label={t("tabloDetail.primaryKeyLabel")}
                    />
                    {column.primaryKey && <span className="pk-badge">PK</span>}
                  </label>
                )}
              />
              <Column
                header={t("tabloDetail.colTag")}
                body={(column: DraftColumn) => {
                  const selectedTag = tags.find((tag) => tag.id === column.tagId);
                  return (
                    <select
                      className="tag-select cursor-pointer"
                      style={selectedTag ? tagColorStyle(selectedTag.name) : undefined}
                      value={column.tagId ?? ""}
                      disabled={column.toDelete}
                      onChange={(e) =>
                        onChangeColumnTag(column.id, e.target.value ? Number(e.target.value) : null)
                      }
                    >
                      <option value="">{t("kolonRow.noTag")}</option>
                      {tags.map((tag) => (
                        <option key={tag.id} value={tag.id} style={tagColorStyle(tag.name)}>
                          {tag.name}
                        </option>
                      ))}
                    </select>
                  );
                }}
              />
              <Column
                body={(column: DraftColumn) =>
                  canWrite && (
                    <button
                      className="btn btn-link btn-danger"
                      onClick={() => onToggleDeleteKolon(column.id)}
                    >
                      {column.toDelete ? t("common.undo") : t("common.delete")}
                    </button>
                  )
                }
              />
            </DataTable>

            {canWrite && (
              <form
                className="add-column-form flex align-items-center"
                onSubmit={handleAddKolonSubmit}
              >
                <InputText
                  type="text"
                  placeholder={t("tabloDetail.columnNamePlaceholder")}
                  value={columnName}
                  onChange={(e) => {
                    clearCustomValidity(e);
                    setKolonName(e.target.value);
                  }}
                  onInvalid={onRequiredInvalid(t)}
                  required
                />
                <select
                  value={columnType}
                  onChange={(e) => setKolonType(e.target.value as ColumnType)}
                >
                  {COLUMN_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
                <label className="checkbox-label">
                  <Checkbox
                    checked={columnPrimaryKey}
                    onChange={(e) => setKolonPrimaryKey(e.checked ?? false)}
                  />
                  {t("tabloDetail.primaryKeyLabel")}
                </label>
                <Button className="btn" type="submit" label={t("tabloDetail.addColumn")} />
              </form>
            )}

            {canWrite && (
              <form
                className="add-tag-form flex align-items-center"
                onSubmit={handleCreateTagSubmit}
              >
                <InputText
                  type="text"
                  placeholder={t("tabloDetail.tagNamePlaceholder")}
                  value={newTagName}
                  onChange={(e) => {
                    clearCustomValidity(e);
                    setNewTagName(e.target.value);
                  }}
                  onInvalid={onRequiredInvalid(t)}
                  required
                />
                <Button className="btn" type="submit" label={t("tabloDetail.createTag")} />
              </form>
            )}
          </div>
        )}

        {activeTab === "data" && (
          <div className="detail-card">
            <div className="table-data-toolbar flex align-items-center justify-content-between">
              <span className="table-data-total-count">
                {tableData ? t("tabloDetail.dataTotalRows", { count: tableData.totalRows }) : ""}
              </span>
              <div className="flex align-items-center table-data-toolbar-right">
                <Button
                  className="btn btn-secondary"
                  type="button"
                  label={t("tabloDetail.dataExportCsv")}
                  loading={exportingCsv}
                  onClick={handleExportCsv}
                />
                {canWrite && (
                  <Button
                    className="btn btn-secondary"
                    type="button"
                    label={t(addRowOpen ? "common.cancel" : "tabloDetail.dataAddRow")}
                    onClick={() => setAddRowOpen((open) => !open)}
                  />
                )}
                <label className="table-data-page-size">
                  <span>{t("tabloDetail.dataPageSize")}</span>
                  <select
                    value={dataPageSize}
                    onChange={(e) => {
                      setDataPageSize(Number(e.target.value) as TableDataPageSize);
                      setDataPage(0);
                    }}
                  >
                    {TABLE_DATA_PAGE_SIZES.map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>

            {addRowOpen && (
              <form className="table-data-add-row-form" onSubmit={handleInsertRowSubmit}>
                <div className="table-data-add-row-fields">
                  {draft.columns.map((column) => (
                    <label key={column.id} className="table-data-add-row-field">
                      <span>
                        {column.name}
                        <span className="table-data-add-row-type"> ({column.type})</span>
                      </span>
                      {column.type === "boolean" ? (
                        <select
                          value={newRowValues[column.name] ?? "false"}
                          onChange={(e) =>
                            setNewRowValues((prev) => ({ ...prev, [column.name]: e.target.value }))
                          }
                        >
                          <option value="false">false</option>
                          <option value="true">true</option>
                        </select>
                      ) : (
                        <InputText
                          type={
                            column.type === "numeric"
                              ? "number"
                              : column.type === "datetime"
                                ? "datetime-local"
                                : "text"
                          }
                          value={newRowValues[column.name] ?? ""}
                          onChange={(e) =>
                            setNewRowValues((prev) => ({ ...prev, [column.name]: e.target.value }))
                          }
                        />
                      )}
                    </label>
                  ))}
                </div>
                <Button
                  className="btn btn-primary"
                  type="submit"
                  label={t("tabloDetail.dataAddRowSubmit")}
                  loading={addingRow}
                />
              </form>
            )}

            {editingRow && (
              <form className="table-data-add-row-form" onSubmit={handleUpdateRowSubmit}>
                <div className="table-data-add-row-fields">
                  {draft.columns
                    .filter((column) => column.primaryKey)
                    .map((column) => (
                      <label key={column.id} className="table-data-add-row-field">
                        <span>
                          {column.name}
                          <span className="table-data-add-row-type">
                            {" "}
                            ({t("tabloDetail.primaryKeyLabel")})
                          </span>
                        </span>
                        <InputText
                          type="text"
                          value={String(editingRow[column.name] ?? "")}
                          disabled
                        />
                      </label>
                    ))}
                  {draft.columns
                    .filter((column) => !column.primaryKey)
                    .map((column) => (
                      <label key={column.id} className="table-data-add-row-field">
                        <span>
                          {column.name}
                          <span className="table-data-add-row-type"> ({column.type})</span>
                        </span>
                        {column.type === "boolean" ? (
                          <select
                            value={editRowValues[column.name] ?? "false"}
                            onChange={(e) =>
                              setEditRowValues((prev) => ({
                                ...prev,
                                [column.name]: e.target.value,
                              }))
                            }
                          >
                            <option value="false">false</option>
                            <option value="true">true</option>
                          </select>
                        ) : (
                          <InputText
                            type={
                              column.type === "numeric"
                                ? "number"
                                : column.type === "datetime"
                                  ? "datetime-local"
                                  : "text"
                            }
                            value={editRowValues[column.name] ?? ""}
                            onChange={(e) =>
                              setEditRowValues((prev) => ({
                                ...prev,
                                [column.name]: e.target.value,
                              }))
                            }
                          />
                        )}
                      </label>
                    ))}
                </div>
                <div className="flex align-items-center table-data-toolbar-right">
                  <Button
                    className="btn btn-primary"
                    type="submit"
                    label={t("tabloDetail.dataEditRowSubmit")}
                    loading={savingEditRow}
                  />
                  <Button
                    className="btn btn-secondary"
                    type="button"
                    label={t("common.cancel")}
                    onClick={() => {
                      setEditingRow(null);
                      setEditRowValues({});
                    }}
                  />
                </div>
              </form>
            )}

            <div className="table-data-scroll-area">
              <DataTable
                value={tableData?.rows ?? []}
                loading={loadingData}
                className="table-data-table w-full"
                emptyMessage={t("tabloDetail.dataEmpty")}
                scrollable
              >
                {(tableData?.columns ?? []).map((col) => (
                  <Column
                    key={col}
                    field={col}
                    header={col}
                    body={(row: Record<string, unknown>) => {
                      const value = row[col];
                      if (value === null || value === undefined) {
                        return <span className="table-data-null">NULL</span>;
                      }
                      if (typeof value === "boolean") {
                        return (
                          <span
                            className={`table-data-bool ${value ? "table-data-bool-true" : "table-data-bool-false"}`}
                          >
                            {String(value)}
                          </span>
                        );
                      }
                      if (typeof value === "number") {
                        return <span className="table-data-number">{value}</span>;
                      }
                      return <span className="table-data-text">{String(value)}</span>;
                    }}
                  />
                ))}
                {canWrite && hasPrimaryKey && (
                  <Column
                    key="__actions"
                    header={t("tabloDetail.dataRowActions")}
                    body={(row: Record<string, unknown>) => (
                      <Button
                        className="btn btn-secondary btn-sm"
                        type="button"
                        label={t("tabloDetail.dataEditRow")}
                        onClick={() => startEditRow(row)}
                      />
                    )}
                  />
                )}
              </DataTable>
            </div>

            <Paginator
              first={dataPage * dataPageSize}
              rows={dataPageSize}
              totalRecords={tableData?.totalRows ?? 0}
              onPageChange={(e: PaginatorPageChangeEvent) => setDataPage(e.page)}
            />
          </div>
        )}
      </fieldset>
    </section>
  );
}
