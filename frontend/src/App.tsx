import { Dashboard } from "./pages/Dashboard";
import { NotificationProvider } from "./notifications/NotificationProvider";
import "./App.css";

function App() {
  return (
    <NotificationProvider>
      <div className="App">
        <header className="app-header">
          <h1>DBAdmin</h1>
        </header>
        <Dashboard />
      </div>
    </NotificationProvider>
  );
}

export default App;
