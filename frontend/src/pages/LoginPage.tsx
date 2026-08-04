import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthProvider";
import { ApiError } from "../api/client";
import { notifyFromError, useNotify } from "../notifications/NotificationProvider";
import { Logo } from "../components/Logo";

/**
 * Giris ekrani. AuthProvider'in "anonymous" durumundayken App.tsx bunu Dashboard yerine
 * gosterir. Basarili giristen sonra AuthProvider kendi state'ini "authenticated"e cevirir,
 * App.tsx bunu izleyip otomatik olarak Dashboard'a gecer — bu component yonlendirme yapmaz.
 */
export function LoginPage() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const notify = useNotify();
  const [kullaniciAdi, setKullaniciAdi] = useState("");
  const [parola, setParola] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await login(kullaniciAdi, parola);
    } catch (err) {
      // AUTH_INVALID_CREDENTIALS icin errors sozlugunde ayri bir metin yok — ApiError.message
      // zaten backend'in "kullanici adi ya da parola hatali" mesaji, ekstra ceviri gerekmiyor.
      if (err instanceof ApiError) {
        notify(err.status, err.message);
      } else {
        notifyFromError(notify, t, err, t("auth.loginFailed"));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <form className="login-form" onSubmit={handleSubmit}>
        <h1>
          <Logo className="brand-mark" />
          DBAdmin
        </h1>
        <label className="login-field">
          <span>{t("auth.username")}</span>
          <input
            type="text"
            autoComplete="username"
            value={kullaniciAdi}
            onChange={(e) => setKullaniciAdi(e.target.value)}
            autoFocus
            required
          />
        </label>
        <label className="login-field">
          <span>{t("auth.password")}</span>
          <input
            type="password"
            autoComplete="current-password"
            value={parola}
            onChange={(e) => setParola(e.target.value)}
            required
          />
        </label>
        <button className="btn btn-primary login-submit" type="submit" disabled={submitting}>
          {submitting ? t("auth.loggingIn") : t("auth.login")}
        </button>
      </form>
    </div>
  );
}
