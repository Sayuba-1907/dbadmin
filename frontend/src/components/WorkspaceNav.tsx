import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthProvider";

/** Dashboard'un su an gosterdigi ana alan: schema/tablo agaci mi, etiket listesi mi, yoksa kullanici yonetimi mi. */
export type WorkspaceView = "schemas" | "tags" | "users";

interface WorkspaceNavProps {
  active: WorkspaceView;
  onChange: (view: WorkspaceView) => void;
}

/**
 * En solda duran ince dikey menu: "Şemalar", "Tagler" ve (sadece ADMIN icin) "Kullanıcılar"
 * arasinda gecis yapar. Kendi state'i yok — hangi gorunumun aktif oldugu Dashboard'da tutulur,
 * bu component sadece secimi gosterir ve tiklamayi yukari bildirir.
 * <p>
 * "Kullanıcılar" butonu {@code isAdmin} degilse hic render edilmez — backend zaten
 * {@code /api/kullanicilar/**}'i ADMIN disina 403 ile kapatiyor, ama VIEWER/EDITOR'a hicbir
 * zaman giremeyecegi bir sekmeyi gostermenin anlami yok.
 */
export function WorkspaceNav({ active, onChange }: WorkspaceNavProps) {
  const { t } = useTranslation();
  const { isAdmin } = useAuth();
  return (
    <nav className="workspace-nav flex flex-column">
      <button
        className={`workspace-nav-btn cursor-pointer text-left${active === "schemas" ? " active" : ""}`}
        onClick={() => onChange("schemas")}
      >
        <span className="nav-icon" aria-hidden="true">
          ▦
        </span>
        {t("nav.schemalar")}
      </button>
      <button
        className={`workspace-nav-btn cursor-pointer text-left${active === "tags" ? " active" : ""}`}
        onClick={() => onChange("tags")}
      >
        <span className="nav-icon" aria-hidden="true">
          ◈
        </span>
        {t("nav.tagler")}
      </button>
      {isAdmin && (
        <button
          className={`workspace-nav-btn cursor-pointer text-left${active === "users" ? " active" : ""}`}
          onClick={() => onChange("users")}
        >
          <span className="nav-icon" aria-hidden="true">
            ◉
          </span>
          {t("nav.kullanicilar")}
        </button>
      )}
    </nav>
  );
}
