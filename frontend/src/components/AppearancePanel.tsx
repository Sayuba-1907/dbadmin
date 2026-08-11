import { useTranslation } from "react-i18next";
import { changeLanguage, SUPPORTED_LANGUAGES, SupportedLanguage } from "../i18n";
import { ThemeSwitcher } from "./ThemeSwitcher";

const LANGUAGE_META: Record<SupportedLanguage, { flag: string; label: string }> = {
  tr: { flag: "🇹🇷", label: "Türkçe" },
  en: { flag: "🇬🇧", label: "English" },
};

/**
 * Requirement notu 9 ("Ayarlar Sayfası") — kullanicinin geri bildirimiyle Profil ve Ayarlar'dan
 * AYRI, kendi hesap-popup girisi olan bir sayfa (bkz. WorkspaceNav). Dil secimi buyuk, bayrakli
 * kartlarla ("çeşitlendirelim" istegi) — LanguageSwitcher'daki kucuk iki butonun aksine, burasi
 * kendi basina bir sayfa oldugu icin daha fazla gorsel alan ayirmak mantikli.
 */
export function AppearancePanel() {
  const { t, i18n } = useTranslation();

  return (
    <section className="appearance-panel fadeinup animation-duration-200">
      <div className="appearance-header flex align-items-center justify-content-between">
        <h2>{t("nav.appearance")}</h2>
      </div>

      <div className="detail-card">
        <h3>{t("settings.languageLabel")}</h3>
        <div className="appearance-language-cards flex">
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang}
              type="button"
              className={`appearance-language-card cursor-pointer${i18n.language === lang ? " active" : ""}`}
              onClick={() => changeLanguage(lang)}
              disabled={i18n.language === lang}
            >
              <span className="appearance-language-flag" aria-hidden="true">
                {LANGUAGE_META[lang].flag}
              </span>
              <span className="appearance-language-label">{LANGUAGE_META[lang].label}</span>
            </button>
          ))}
        </div>
      </div>

      <div className="detail-card">
        <h3>{t("settings.themeLabel")}</h3>
        <ThemeSwitcher />
      </div>
    </section>
  );
}
