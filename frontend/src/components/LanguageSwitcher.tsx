import { useTranslation } from "react-i18next";
import { changeLanguage, SUPPORTED_LANGUAGES, SupportedLanguage } from "../i18n";

const LABELS: Record<SupportedLanguage, string> = {
  tr: "TR",
  en: "EN",
};

/**
 * Sag ust kosedeki dil sectici. Kendi metnini ceviriye sokmuyoruz — bir dil sectici her
 * zaman dillerin kendi kisa adini gosterir (TR/EN), o an aktif olan arayuz diline gore
 * degismez; bu standart bir UX kuralidir.
 * `i18n.language` degistiginde react-i18next tum agaci otomatik yeniden render eder,
 * bu component sadece hangi butonun "aktif" gorunecegini o degere bakarak belirler.
 */
export function LanguageSwitcher() {
  const { i18n } = useTranslation();

  return (
    <div className="language-switcher" role="group" aria-label="Language selector">
      {SUPPORTED_LANGUAGES.map((lang) => (
        <button
          key={lang}
          type="button"
          className={`language-switcher-btn${i18n.language === lang ? " active" : ""}`}
          onClick={() => changeLanguage(lang)}
          disabled={i18n.language === lang}
        >
          {LABELS[lang]}
        </button>
      ))}
    </div>
  );
}
