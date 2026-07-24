import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Tablo } from "../api/tablolar";

/**
 * Props: gercek veri (tablolar, selectedId) ve "bir seye tikladiginda ne olsun" callback'leri
 * hep Dashboard'dan gelir. Bu component'in kendi tuttugu tek state, arama kutusunun metni —
 * o da salt gorsel/gecici bir UI durumu, Dashboard'un ya da backend'in hic umrunda degil.
 */
interface TabloSidebarProps {
  tablolar: Tablo[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreateClick: () => void;
}

/** Sol tarafta tablo listesi + "yeni tablo" butonu + arama kutusu. Secili tablo `selected` class'iyla vurgulanir. */
export function TabloSidebar({ tablolar, selectedId, onSelect, onCreateClick }: TabloSidebarProps) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");

  // Buyuk/kucuk harf duyarsiz, basit bir "icinde geciyor mu" filtresi — backend'e hic gitmiyor,
  // tamamen zaten ekranda olan listenin uzerinde calisiyor.
  const normalizedQuery = query.trim().toLowerCase();
  const filteredTablolar = normalizedQuery
    ? tablolar.filter((tablo) => tablo.name.toLowerCase().includes(normalizedQuery))
    : tablolar;

  return (
    <aside className="sidebar">
      <button className="btn btn-primary" onClick={onCreateClick}>
        {t("sidebar.newTable")}
      </button>
      {tablolar.length > 0 && (
        <input
          type="text"
          className="sidebar-search"
          placeholder={t("sidebar.searchPlaceholder")}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      )}
      <ul className="tablo-list">
        {filteredTablolar.map((tablo) => (
          <li key={tablo.id}>
            <button
              className={`tablo-list-item${tablo.id === selectedId ? " selected" : ""}`}
              onClick={() => onSelect(tablo.id)}
            >
              {tablo.name}
              <span className="kolon-count">{tablo.kolonlar.length}</span>
            </button>
          </li>
        ))}
        {tablolar.length === 0 && <li className="empty-hint">{t("sidebar.empty")}</li>}
        {tablolar.length > 0 && filteredTablolar.length === 0 && (
          <li className="empty-hint">{t("sidebar.noSearchResults")}</li>
        )}
      </ul>
    </aside>
  );
}
