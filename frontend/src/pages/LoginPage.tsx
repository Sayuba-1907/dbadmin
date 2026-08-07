import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { InputText } from "primereact/inputtext";
import { Password } from "primereact/password";
import { Button } from "primereact/button";
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
  const [username, setKullaniciAdi] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await login(username, password);
    } catch (err) {
      // AUTH_INVALID_CREDENTIALS icin errors sozlugunde ayri bir metin yok — ApiError.message
      // zaten backend'in "kullanici adi ya da password hatali" mesaji, ekstra ceviri gerekmiyor.
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
    <div className="login-page flex align-items-center justify-content-center">
      <form className="login-form flex flex-column" onSubmit={handleSubmit}>
        <h1>
          <Logo className="brand-mark inline-block" />
          DBAdmin
        </h1>
        <label className="login-field">
          <span>{t("auth.username")}</span>
          <InputText
            className="w-full"
            autoComplete="username"
            value={username}
            onChange={(e) => setKullaniciAdi(e.target.value)}
            autoFocus
            required
          />
        </label>
        <label className="login-field">
          <span>{t("auth.password")}</span>
          {/* feedback={false}: PrimeReact'in varsayilan "password gucu" overlay panelini
              kapatiyoruz — o panel icin de ConfirmDialog/Toast'ta oldugu gibi ayri bir
              unstyled-mode reskin ugrasi gerekirdi, kazanci yokken riski var. toggleMask
              ise sadece bir buton (overlay degil), goz ikonuyla goster/gizle bedavaya geliyor. */}
          <Password
            inputId="login-password"
            className="login-password block w-full relative"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            feedback={false}
            toggleMask
            required
          />
        </label>
        <Button
          className="btn btn-primary login-submit"
          type="submit"
          disabled={submitting}
          label={submitting ? t("auth.loggingIn") : t("auth.login")}
        />
      </form>
    </div>
  );
}
