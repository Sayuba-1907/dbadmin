import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthProvider";

/** Dashboard'un su an gosterdigi ana alan: schema/tablo agaci mi, etiket listesi mi, kullanici yonetimi mi, yoksa bakim sayfasi mi. */
export type WorkspaceView = "schemas" | "tags" | "users" | "maintenance";

interface WorkspaceNavProps {
  active: WorkspaceView;
  onChange: (view: WorkspaceView) => void;
}

/**
 * En solda duran ince dikey menu: "Şemalar", "Tagler" ve (sadece ADMIN icin) "Kullanıcılar" /
 * "Bakım" arasinda gecis yapar. Kendi state'i yok — hangi gorunumun aktif oldugu Dashboard'da
 * tutulur, bu component sadece secimi gosterir ve tiklamayi yukari bildirir.
 * <p>
 * "Kullanıcılar"/"Bakım" butonlari {@code isAdmin} degilse hic render edilmez — backend zaten
 * {@code /api/users/**} ve {@code /api/maintenance/**}'i ADMIN disina 403 ile kapatiyor, ama
 * VIEWER/EDITOR'a hicbir zaman giremeyecegi bir sekmeyi gostermenin anlami yok.
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
      {isAdmin && (
        <button
          className={`workspace-nav-btn cursor-pointer text-left${active === "maintenance" ? " active" : ""}`}
          onClick={() => onChange("maintenance")}
        >
          <span className="nav-icon" aria-hidden="true">
            ⚙
          </span>
          {t("nav.maintenance")}
        </button>
      )}
    </nav>
  );
}
