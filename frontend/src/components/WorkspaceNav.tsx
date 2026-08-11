import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthProvider";

/**
 * Dashboard'un su an gosterdigi ana alan: schema/tablo agaci mi, etiket listesi mi, kullanici
 * yonetimi mi, yoksa hesap popup'undaki sayfalardan (Profil/Görünüm/Oturumlar/Yönetim/Faydalı
 * Linkler) biri mi — requirement notu 9 ("Ayarlar Sayfası"), kullanicinin geri bildirimiyle
 * UCUNCU KEZ sekillendi: artik tek bir "settings" sayfasi YOK, "Ayarlar" hesap popup'unda
 * Claude Code'un "Language" menusune benzer bir FLYOUT (bkz. workspace-nav-settings-flyout) —
 * Oturumlar/Yönetim/Faydalı Linkler ucu de kendi ayri sayfalari.
 */
export type WorkspaceView =
  "schemas" | "tags" | "users" | "profile" | "appearance" | "sessions" | "admin" | "links";

interface WorkspaceNavProps {
  active: WorkspaceView;
  onChange: (view: WorkspaceView) => void;
}

/**
 * En solda duran ince dikey menu: "Şemalar", "Tagler" ve (sadece ADMIN icin) "Kullanıcılar"
 * arasinda gecis yapar; en altta da her kullaniciya acik bir hesap karti var — Claude Code'un
 * sol-alt hesap kartina benzer bir desen (kullanicinin acikca istedigi tasarim): tiklaninca
 * ustunde acilan popup'ta Profil/Görünüm dogrudan link, "Ayarlar" ise KENDI SAYFASI OLMAYAN bir
 * flyout tetikleyicisi — uzerine tiklaninca yaninda Oturumlar/Yönetim/Faydalı Linkler acilir
 * (Claude'daki "Language" alt-menusuyle ayni fikir). Kendi state'i (aktif secim) yok — hangi
 * gorunumun aktif oldugu Dashboard'da tutulur, bu component sadece secimi gosterir ve tiklamayi
 * yukari bildirir.
 * <p>
 * "Kullanıcılar" butonu ve flyout'taki "Yönetim"/"Faydalı Linkler" secenekleri {@code isAdmin}
 * degilse hic render edilmez — backend zaten {@code /api/users/**} ve {@code
 * /api/maintenance/**}'i ADMIN disina 403 ile kapatiyor, ama VIEWER/EDITOR'a hicbir zaman
 * giremeyecegi bir sekmeyi gostermenin anlami yok. "Oturumlar" ise herkese acik (kendi
 * oturumlarin).
 */
export function WorkspaceNav({ active, onChange }: WorkspaceNavProps) {
  const { t } = useTranslation();
  const { isAdmin, username, role } = useAuth();
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);
  const [settingsFlyoutOpen, setSettingsFlyoutOpen] = useState(false);
  const accountMenuRef = useRef<HTMLDivElement>(null);

  // Disariya tiklaninca kapat — NotificationBell'deki ayni desen. Tek ref yeterli: flyout,
  // popup'in ICINDE (DOM olarak alt eleman) render oluyor, yani popup'a tiklamak flyout'u da
  // "disarida degil" sayar.
  useEffect(() => {
    if (!accountMenuOpen) {
      return;
    }
    function handleClickOutside(event: MouseEvent) {
      if (accountMenuRef.current && !accountMenuRef.current.contains(event.target as Node)) {
        setAccountMenuOpen(false);
        setSettingsFlyoutOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [accountMenuOpen]);

  function handleSelect(view: WorkspaceView) {
    setAccountMenuOpen(false);
    setSettingsFlyoutOpen(false);
    onChange(view);
  }

  const accountActive =
    active === "profile" ||
    active === "appearance" ||
    active === "sessions" ||
    active === "admin" ||
    active === "links";

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
      {/* margin-top:auto ile en alta itiliyor (bkz. App.css .workspace-nav-account). */}
      <div className="workspace-nav-account" ref={accountMenuRef}>
        {accountMenuOpen && (
          <div className="workspace-nav-account-menu" role="menu">
            <div className="workspace-nav-account-identity">
              <span className="workspace-nav-account-username">{username}</span>
              <span className="workspace-nav-account-role">{role}</span>
            </div>
            <button
              type="button"
              role="menuitem"
              className="workspace-nav-account-menu-item cursor-pointer text-left"
              onClick={() => handleSelect("profile")}
            >
              <span className="nav-icon" aria-hidden="true">
                ◍
              </span>
              {t("nav.profile")}
            </button>
            <button
              type="button"
              role="menuitem"
              className="workspace-nav-account-menu-item cursor-pointer text-left"
              onClick={() => handleSelect("appearance")}
            >
              <span className="nav-icon" aria-hidden="true">
                ◐
              </span>
              {t("nav.appearance")}
            </button>
            <div className="workspace-nav-account-submenu-wrapper">
              <button
                type="button"
                role="menuitem"
                aria-haspopup="true"
                aria-expanded={settingsFlyoutOpen}
                className={`workspace-nav-account-menu-item cursor-pointer text-left${settingsFlyoutOpen ? " open" : ""}`}
                onClick={() => setSettingsFlyoutOpen((open) => !open)}
              >
                <span className="nav-icon" aria-hidden="true">
                  ⚙
                </span>
                {t("nav.settings")}
                <span className="workspace-nav-account-submenu-arrow" aria-hidden="true">
                  ›
                </span>
              </button>
              {settingsFlyoutOpen && (
                <div className="workspace-nav-account-submenu" role="menu">
                  <button
                    type="button"
                    role="menuitem"
                    className="workspace-nav-account-menu-item cursor-pointer text-left"
                    onClick={() => handleSelect("sessions")}
                  >
                    {t("settings.tabSessions")}
                  </button>
                  {isAdmin && (
                    <button
                      type="button"
                      role="menuitem"
                      className="workspace-nav-account-menu-item cursor-pointer text-left"
                      onClick={() => handleSelect("admin")}
                    >
                      {t("settings.tabAdmin")}
                    </button>
                  )}
                  {isAdmin && (
                    <button
                      type="button"
                      role="menuitem"
                      className="workspace-nav-account-menu-item cursor-pointer text-left"
                      onClick={() => handleSelect("links")}
                    >
                      {t("settings.adminLinksTitle")}
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
        <button
          className={`workspace-nav-btn cursor-pointer text-left${accountActive ? " active" : ""}`}
          onClick={() => setAccountMenuOpen((open) => !open)}
        >
          <span className="nav-icon" aria-hidden="true">
            ◍
          </span>
          {username}
        </button>
      </div>
    </nav>
  );
}
