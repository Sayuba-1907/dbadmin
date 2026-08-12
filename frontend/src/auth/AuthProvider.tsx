import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { LoginResult, Role, me, login as apiLogin } from "../api/auth";
import { setAuthToken, setOnUnauthorized } from "../api/client";
import { useNotify } from "../notifications/NotificationProvider";

/** Sayfa yenilendiginde giris durumunu hatirlamak icin token buraya yazilir. */
const STORAGE_KEY = "dbadmin-token";

/**
 * "loading": sayfa yeni acildi, localStorage'daki token'in (varsa) hala gecerli olup olmadigi
 * /api/auth/me ile dogrulaniyor — bu bitene kadar ekranda ne login formu ne Dashboard gosterilir,
 * yoksa gecerli bir oturumu olan kullaniciya bir an icin login formu goruntulenirdi.
 */
type AuthStatus = "loading" | "anonymous" | "authenticated";

export interface AuthContextValue {
  status: AuthStatus;
  id: number | null;
  username: string | null;
  fullName: string | null;
  email: string | null;
  hasAvatar: boolean;
  role: Role | null;
  /** role EDITOR ya da ADMIN ise true — "kimisi update edebilsin kimisi edemesin" burada okunur. */
  canWrite: boolean;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  /**
   * ProfilePanel'in updateProfile/uploadAvatar sonrasi cagirdigi kanca — sunucudan gelen
   * TAZE {@link LoginResult}'u context state'ine yansitir. {@code token} doluysa (kullanici
   * adi degistiyse, bkz. AuthController#updateProfile javadoc'u) localStorage'daki eski
   * token'in yerine SESSIZCE yazilir — logout/tekrar-login gerekmez.
   */
  applyProfileUpdate: (result: LoginResult) => void;
}

/**
 * Export edilmesinin tek sebebi testler: Dashboard.test.tsx gibi testler gercek AuthProvider'in
 * localStorage/`/api/auth/me` akisina girmeden, sabit bir rolle (ör. EDITOR) dogrudan
 * {@code <AuthContext.Provider value={...}>} ile sarmalayabilsin diye. Uygulama kodu bunun
 * yerine hep {@link useAuth} kullanmali.
 */
export const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * App.tsx'te tum uygulamayi sarmalayan Provider (NotificationProvider'in icinde, cunku
 * oturum suresi dolunca bir bildirim gostermek icin {@link useNotify}'a ihtiyaci var).
 * Token'i kendi state'inde degil localStorage'da tutar (sayfa yenilenince kaybolmasin), ama
 * her render'da localStorage okumak yerine tek gercek kaynak olarak component state'ini kullanir
 * — localStorage sadece yeniden yuklemede baslangic degerini vermek icin okunur.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [id, setId] = useState<number | null>(null);
  const [username, setKullaniciAdi] = useState<string | null>(null);
  const [fullName, setFullName] = useState<string | null>(null);
  const [email, setEmail] = useState<string | null>(null);
  const [hasAvatar, setHasAvatar] = useState(false);
  const [role, setRol] = useState<Role | null>(null);
  const notify = useNotify();
  const { t } = useTranslation();
  // onUnauthorized kancasi kapanma (closure) icinde kuruluyor ve status'un o anki degerini
  // gormesi gerekiyor — state'in kendisini deps'e koymak kancayi her status degisiminde yeniden
  // kaydetmek demek olurdu, bunun yerine hep guncel kalan bir ref okunuyor.
  const statusRef = useRef(status);
  statusRef.current = status;

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setAuthToken(null);
    setId(null);
    setKullaniciAdi(null);
    setFullName(null);
    setEmail(null);
    setHasAvatar(false);
    setRol(null);
    setStatus("anonymous");
  }, []);

  // client.ts, token gecersiz/suresi dolmus bir 401 (AUTH_REQUIRED) aldiginda burayi cagirir.
  // Daha once giris yapilmissa kullaniciyi bilgilendiriyoruz; login formundaki yanlis password
  // denemesi bu yola hic girmez (bkz. client.ts'teki AUTH_INVALID_CREDENTIALS ayrimi).
  useEffect(() => {
    setOnUnauthorized(() => {
      if (statusRef.current === "authenticated") {
        notify(401, t("auth.sessionExpired"));
      }
      logout();
    });
    return () => setOnUnauthorized(null);
  }, [logout, notify, t]);

  // Sayfa ilk acildiginda localStorage'da token varsa gecerliligini /api/auth/me ile dogrular
  // (role bu arada degismis olabilir, token suresi dolmus olabilir). Bilerek sadece bir kez
  // (mount'ta) calisir.
  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) {
      setStatus("anonymous");
      return;
    }
    setAuthToken(stored);
    me()
      .then((result) => {
        setId(result.id);
        setKullaniciAdi(result.username);
        setFullName(result.fullName);
        setEmail(result.email);
        setHasAvatar(result.hasAvatar);
        setRol(result.role);
        setStatus("authenticated");
      })
      .catch(() => logout());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (usernameInput: string, password: string) => {
    const result = await apiLogin(usernameInput, password);
    // token login'de her zaman dolu gelir (bkz. LoginResult) — non-null assertion burada guvenli.
    localStorage.setItem(STORAGE_KEY, result.token as string);
    setAuthToken(result.token);
    setId(result.id);
    setKullaniciAdi(result.username);
    setFullName(result.fullName);
    setEmail(result.email);
    setHasAvatar(result.hasAvatar);
    setRol(result.role);
    setStatus("authenticated");
  }, []);

  /** ProfilePanel'in updateProfile/uploadAvatar sonrasi cagirdigi kanca — bkz. AuthContextValue javadoc'u. */
  const applyProfileUpdate = useCallback((result: LoginResult) => {
    if (result.token) {
      localStorage.setItem(STORAGE_KEY, result.token);
      setAuthToken(result.token);
    }
    setId(result.id);
    setKullaniciAdi(result.username);
    setFullName(result.fullName);
    setEmail(result.email);
    setHasAvatar(result.hasAvatar);
    setRol(result.role);
  }, []);

  const value: AuthContextValue = {
    status,
    id,
    username,
    fullName,
    email,
    hasAvatar,
    role,
    canWrite: role === "EDITOR" || role === "ADMIN",
    isAdmin: role === "ADMIN",
    login,
    logout,
    applyProfileUpdate,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
