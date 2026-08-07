import { useState } from "react";
import { useTranslation } from "react-i18next";
import { PrimeReactProvider } from "primereact/api";
import { Dashboard } from "./pages/Dashboard";
import { LoginPage } from "./pages/LoginPage";
import { AppNotification } from "./api/notifications";
import { NotificationProvider, useNotify } from "./notifications/NotificationProvider";
import { ConfirmProvider } from "./notifications/ConfirmProvider";
import { AuthProvider, useAuth } from "./auth/AuthProvider";
import { NotificationBell } from "./components/NotificationBell";
import { LanguageSwitcher } from "./components/LanguageSwitcher";
import { ThemeSwitcher } from "./components/ThemeSwitcher";
import { ThemeProvider } from "./theme/ThemeProvider";
import { Logo } from "./components/Logo";
import { useNotifications } from "./hooks/useNotifications";
import "./App.css";

/**
 * AuthProvider'in durumuna gore ne gosterilecegine karar verir. Router yok (tek sayfalik
 * uygulama) — gecis tamamen {@code status}'a bagli bir kosullu render.
 */
function AppContent() {
  const { t } = useTranslation();
  const { status, username, role, logout } = useAuth();
  const notify = useNotify();
  // Dashboard'u yeniden mount ederek "ana sayfa"ya donusu tetikler — router olmadigi icin
  // Dashboard'un ic state'ine (activeView/selectedId) disaridan mudahale etmek yerine, key'i
  // degistirip Dashboard'un kendi useState varsayilanlarina (schemalar view, secim yok) sifirdan
  // baslamasini sagliyoruz.
  const [homeKey, setHomeKey] = useState(0);
  // Bir bildirime tiklaninca hangi tabloya gidilecegi — Dashboard'un kendi ic state'i
  // (selectedId/activeView) App.tsx'ten disaridan degistirilemedigi icin, "git" istegini bir
  // prop olarak asagi (Dashboard'a) tasiyoruz; Dashboard bunu tuketince onNavigated ile temizler.
  const [navigateToTableId, setNavigateToTabloId] = useState<number | null>(null);
  const { count, list, loading, refresh, markAsRead, markAllAsRead } = useNotifications(
    status === "authenticated",
    (n: AppNotification) =>
      notify(
        200,
        t("notificationBell.newNotificationToast", {
          user: n.triggeredByUsername,
          message: n.message,
        })
      )
  );

  function handleNotificationClick(notification: AppNotification) {
    if (!notification.isRead) {
      markAsRead(notification.id);
    }
    setNavigateToTabloId(notification.tableId);
  }

  if (status === "loading") {
    // localStorage'daki token'in gecerliligi /api/auth/me ile dogrulanirken kisa bir an —
    // bu adim atlanirsa gecerli bir oturumu olan kullaniciya bir yanip sonme (login formu,
    // sonra Dashboard) goruntusu yasatilirdi. Tam ekran ortalanmis, App'in kendi koyu zemininde
    // gosteriliyor ki index.html'deki FOUC-onleme rengiyle kesintisiz devam etsin.
    return (
      <div className="app-loading flex flex-column align-items-center justify-content-center">
        <span className="spinner" aria-hidden="true" />
        <span>{t("dashboard.loading")}</span>
      </div>
    );
  }

  if (status === "anonymous") {
    return <LoginPage />;
  }

  return (
    <div className="App flex flex-column min-h-screen">
      <header className="app-header flex align-items-center justify-content-between">
        <h1>
          <button className="brand-button cursor-pointer" onClick={() => setHomeKey((k) => k + 1)}>
            <Logo className="brand-mark inline-block" />
            DBAdmin
          </button>
        </h1>
        <div className="app-header-right flex align-items-center">
          <NotificationBell
            count={count}
            list={list}
            loading={loading}
            onOpen={refresh}
            onNotificationClick={handleNotificationClick}
            onMarkAllAsRead={markAllAsRead}
          />
          <span className="current-user">
            {username} · {role}
          </span>
          <button className="btn btn-link" onClick={logout}>
            {t("auth.logout")}
          </button>
          <ThemeSwitcher />
          <LanguageSwitcher />
        </div>
      </header>
      <Dashboard
        key={homeKey}
        navigateToTableId={navigateToTableId}
        onNavigated={() => setNavigateToTabloId(null)}
      />
    </div>
  );
}

/**
 * {@link NotificationProvider} en disarida: {@link AuthProvider} oturum suresi dolunca
 * bildirim gosterebilmek icin useNotify'a ihtiyac duyuyor, o yuzden onun icinde olmali.
 * <p>
 * {@code unstyled: true} — PrimeReact bilesenlerinin (ör. TableDetail'deki DataTable) kendi
 * hazir temasini (Material/Bootstrap/PrimeOne) hic yuklemiyoruz; bilesenler CSS'siz, ciplak
 * yapida geliyor ve mevcut tasarim sistemimizin (tokens.css, App.css) class'larini kullanarak
 * biz giydiriyoruz — boylece DBAdmin'in kendi kimligiyle (slate+yesil, monospace) cakismiyor.
 */
function App() {
  return (
    <ThemeProvider>
      <PrimeReactProvider value={{ unstyled: true }}>
        <NotificationProvider>
          <ConfirmProvider>
            <AuthProvider>
              <AppContent />
            </AuthProvider>
          </ConfirmProvider>
        </NotificationProvider>
      </PrimeReactProvider>
    </ThemeProvider>
  );
}

export default App;
