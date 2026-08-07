import { createContext, ReactNode, useCallback, useContext, useState } from "react";

export type Theme = "dark" | "light";

const STORAGE_KEY = "dbadmin-theme";

interface ThemeContextValue {
  theme: Theme;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/**
 * Baslangic degerini <html data-theme="..."> attribute'undan okur, KENDISI HESAPLAMAZ —
 * o hesap zaten index.html'deki inline script'te (localStorage tercihi, yoksa
 * prefers-color-scheme) sayfa boyanmadan once yapilip DOM'a yazildi (FOUC onlemi). Burada
 * ayni mantigi ikinci kez calistirmak, ikisi arasinda bir tutarsizlik ihtimali dogurur —
 * tek doğruluk kaynagi (source of truth) DOM'daki attribute olmali.
 */
function readInitialTheme(): Theme {
  return document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(readInitialTheme);

  const toggleTheme = useCallback(() => {
    setTheme((prev) => {
      const next: Theme = prev === "dark" ? "light" : "dark";
      document.documentElement.setAttribute("data-theme", next);
      localStorage.setItem(STORAGE_KEY, next);
      return next;
    });
  }, []);

  return <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used within a ThemeProvider");
  }
  return ctx;
}
