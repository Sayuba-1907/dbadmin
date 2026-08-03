import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Dashboard } from "./pages/Dashboard";
import { LoginPage } from "./pages/LoginPage";
import { NotificationProvider } from "./notifications/NotificationProvider";
import { AuthProvider, useAuth } from "./auth/AuthProvider";
import { LanguageSwitcher } from "./components/LanguageSwitcher";
import "./App.css";

/**
 * AuthProvider'in durumuna gore ne gosterilecegine karar verir. Router yok (tek sayfalik
 * uygulama) — gecis tamamen {@code status}'a bagli bir kosullu render.
 */
function AppContent() {
  const { t } = useTranslation();
  const { status, kullaniciAdi, rol, logout } = useAuth();
  // Dashboard'u yeniden mount ederek "ana sayfa"ya donusu tetikler — router olmadigi icin
  // Dashboard'un ic state'ine (activeView/selectedId) disaridan mudahale etmek yerine, key'i
  // degistirip Dashboard'un kendi useState varsayilanlarina (schemalar view, secim yok) sifirdan
  // baslamasini sagliyoruz.
  const [homeKey, setHomeKey] = useState(0);

  if (status === "loading") {
    // localStorage'daki token'in gecerliligi /api/auth/ben ile dogrulanirken kisa bir an —
    // bu adim atlanirsa gecerli bir oturumu olan kullaniciya bir yanip sonme (login formu,
    // sonra Dashboard) goruntusu yasatilirdi.
    return <p className="loading-hint">{t("dashboard.loading")}</p>;
  }

  if (status === "anonymous") {
    return <LoginPage />;
  }

  return (
    <div className="App">
      <header className="app-header">
        <h1>
          <button className="brand-button" onClick={() => setHomeKey((k) => k + 1)}>
            <span className="brand-mark" aria-hidden="true" />
            DBAdmin
          </button>
        </h1>
        <div className="app-header-right">
          <span className="current-user">
            {kullaniciAdi} · {rol}
          </span>
          <button className="btn btn-link" onClick={logout}>
            {t("auth.logout")}
          </button>
          <LanguageSwitcher />
        </div>
      </header>
      <Dashboard key={homeKey} />
    </div>
  );
}

/**
 * {@link NotificationProvider} en disarida: {@link AuthProvider} oturum suresi dolunca
 * bildirim gosterebilmek icin useNotify'a ihtiyac duyuyor, o yuzden onun icinde olmali.
 */
function App() {
  return (
    <NotificationProvider>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </NotificationProvider>
  );
}

export default App;
