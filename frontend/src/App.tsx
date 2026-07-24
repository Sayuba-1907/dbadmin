import { Dashboard } from "./pages/Dashboard";
import { NotificationProvider } from "./notifications/NotificationProvider";
import { LanguageSwitcher } from "./components/LanguageSwitcher";
import "./App.css";

/**
 * Uygulamanin en tepesindeki component. Router yok (tek sayfalik uygulama) — tum ekran
 * {@link Dashboard} icinde. {@link NotificationProvider} her seyi sarmalayarak, altindaki
 * her component'in (Context API sayesinde) bildirim gostermesini mumkun kilar.
 */
function App() {
  return (
    <NotificationProvider>
      <div className="App">
        <header className="app-header">
          <h1>DBAdmin</h1>
          <LanguageSwitcher />
        </header>
        <Dashboard />
      </div>
    </NotificationProvider>
  );
}

export default App;
