import { Tablo } from "../api/tablolar";

interface TabloSidebarProps {
  tablolar: Tablo[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreateClick: () => void;
}

export function TabloSidebar({
  tablolar,
  selectedId,
  onSelect,
  onCreateClick,
}: TabloSidebarProps) {
  return (
    <aside className="sidebar">
      <button className="btn btn-primary" onClick={onCreateClick}>
        + Yeni Tablo
      </button>
      <ul className="tablo-list">
        {tablolar.map((tablo) => (
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
        {tablolar.length === 0 && <li className="empty-hint">Henuz tablo yok</li>}
      </ul>
    </aside>
  );
}
