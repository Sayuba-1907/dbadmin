import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import "./confirm.css";

interface PendingConfirm {
  message: string;
  resolve: (confirmed: boolean) => void;
}

/** window.confirm'in yerini tutan Promise tabanli fonksiyon — native tarayici dialogu (native
 * confirm senkron oldugu icin animasyon/tema uygulanamiyor, koyu tema kimligiyle de uyumsuz)
 * yerine kendi tasarim sistemimizdeki bir modal gosterip kullanicinin secimini bekler. */
type ConfirmFn = (message: string) => Promise<boolean>;

const ConfirmContext = createContext<ConfirmFn | null>(null);

/**
 * App.tsx'te NotificationProvider'in yanina, tum uygulamayi sarmalayan ikinci bir Provider.
 * Ayni anda tek bir onay penceresi olabilir (window.confirm da zaten senkron/tekildi) — bu
 * yuzden tek bir `pending` state yeterli, bildirimlerdeki gibi bir yigina gerek yok.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [pending, setPending] = useState<PendingConfirm | null>(null);

  const confirm = useCallback<ConfirmFn>((message) => {
    return new Promise<boolean>((resolve) => {
      setPending({ message, resolve });
    });
  }, []);

  function answer(confirmed: boolean) {
    pending?.resolve(confirmed);
    setPending(null);
  }

  // Escape ile iptal — native confirm'de de Esc iptal anlamina gelirdi, aynı beklentiyi koruyoruz.
  useEffect(() => {
    if (!pending) {
      return;
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        answer(false);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pending]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending && (
        <div className="confirm-overlay" onClick={() => answer(false)}>
          <div
            className="confirm-modal"
            role="alertdialog"
            aria-modal="true"
            aria-label={pending.message}
            onClick={(e) => e.stopPropagation()}
          >
            <p className="confirm-message">{pending.message}</p>
            <div className="confirm-actions">
              <button type="button" className="btn" onClick={() => answer(false)}>
                {t("common.cancel")}
              </button>
              <button
                type="button"
                className="btn btn-danger"
                autoFocus
                onClick={() => answer(true)}
              >
                {t("common.delete")}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}

/** window.confirm(message) yerine: await confirm(message) — Promise<boolean> doner. */
export function useConfirm(): ConfirmFn {
  const confirm = useContext(ConfirmContext);
  if (!confirm) {
    throw new Error("useConfirm must be used within a ConfirmProvider");
  }
  return confirm;
}
