import { useTranslation } from "react-i18next";
import { useTheme } from "../theme/ThemeProvider";

/**
 * Sag ust kosedeki tema sectici, LanguageSwitcher ile ayni gorsel desen (iki secenekli,
 * aktif olan disabled/vurgulu buton grubu). Tek bir "degistir" butonu yerine iki ayri buton
 * kullanmamizin nedeni de ayni: hangi temanin su an aktif oldugu her zaman gorunur olsun,
 * kullanici mevcut durumu simgeden tahmin etmek zorunda kalmasin.
 */
export function ThemeSwitcher() {
  const { t } = useTranslation();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="theme-switcher" role="group" aria-label={t("theme.switcherLabel")}>
      <button
        type="button"
        className={`theme-switcher-btn${theme === "dark" ? " active" : ""}`}
        onClick={() => theme !== "dark" && toggleTheme()}
        disabled={theme === "dark"}
      >
        {t("theme.dark")}
      </button>
      <button
        type="button"
        className={`theme-switcher-btn${theme === "light" ? " active" : ""}`}
        onClick={() => theme !== "light" && toggleTheme()}
        disabled={theme === "light"}
      >
        {t("theme.light")}
      </button>
    </div>
  );
}
