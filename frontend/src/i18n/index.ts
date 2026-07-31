import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import tr from "./locales/tr.json";
import en from "./locales/en.json";

export const SUPPORTED_LANGUAGES = ["tr", "en"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

const STORAGE_KEY = "dbadmin-language";

/**
 * Sayfa daha once dil sectiyse (localStorage'da kayitliysa) onu, yoksa varsayilan
 * olarak "tr"yi dondurur. index.tsx acilista bunu i18next'e baslangic dili olarak verir,
 * boylece sayfa yenilense bile secilen dil unutulmaz.
 */
function getInitialLanguage(): SupportedLanguage {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "tr" || stored === "en") {
    return stored;
  }
  return "tr";
}

/**
 * i18next kurulumu: react-i18next eklentisiyle React'e baglanir, iki dilin de tum
 * ceviri sozlugunu (tr.json/en.json) hafizaya yukler. Ayri bir HTTP istegiyle ceviri
 * dosyasi cekmiyoruz (lazy-load yok) — proje kucuk oldugu icin ikisi de bundle'a gomulu.
 */
i18n.use(initReactI18next).init({
  resources: {
    tr: { translation: tr },
    en: { translation: en },
  },
  lng: getInitialLanguage(),
  fallbackLng: "tr",
  interpolation: {
    escapeValue: false, // React zaten JSX'i escape ediyor, i18next'in ayrica escape etmesine gerek yok.
  },
});

/** Dili degistirir ve secimi localStorage'a yazar; sayfa yenilendiginde getInitialLanguage bunu okur. */
export function changeLanguage(language: SupportedLanguage) {
  localStorage.setItem(STORAGE_KEY, language);
  i18n.changeLanguage(language);
  document.documentElement.lang = language;
}

document.documentElement.lang = i18n.language;

export default i18n;
