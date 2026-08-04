import { ReactNode, createContext, useCallback, useContext, useRef } from "react";
import { TFunction } from "i18next";
import { Toast, ToastMessage } from "primereact/toast";
import { ApiError } from "../api/client";
import "./notifications.css";

type NotificationKind = "success" | "conflict" | "error";

/** Bildirimin icine konabilecek tek bir aksiyon butonu (mesela "Geri Al"). */
interface NotificationAction {
  label: string;
  onClick: () => void;
}

/** action opsiyonel: cogu bildirim (basari/hata) sadece bilgilendirir, sadece geri alinabilir islemler bir aksiyon tasir. */
type NotifyFn = (status: number, message: string, action?: NotificationAction) => void;

/**
 * Bildirimin ekranda kalma suresi — geri alinabilir bir islemde bu ayni zamanda "geri alma
 * penceresi"dir. Export ediliyor ki mesela Dashboard'daki gecikmeli silme, gercek API cagrisini
 * tam bu bildirim kaybolurken tetikleyecek sekilde ayni degeri kullanabilsin. PrimeReact'in
 * Toast'u {@code life} prop'uyla bu sureyi kendi yonetiyor — eskiden kendi yazdigimiz
 * setTimeout+leaving-state mekanigi tamamen kalkti.
 */
export const NOTIFICATION_DURATION_MS = 5000;

const NotificationContext = createContext<NotifyFn | null>(null);

/** HTTP status kodunu bildirim rengine cevirir — assignment'in istedigi "cevap durumuna gore renkli bildirim" ozelligi burada. */
function kindForStatus(status: number): NotificationKind {
  if (status < 300) {
    return "success";
  }
  if (status === 409) {
    return "conflict";
  }
  return "error";
}

/**
 * Erisilebilirlik (a11y): renk tek basina anlam tasimamali — kirmizi-yesil renk korlugu
 * olan biri (en yaygin tur) basari ile hatayi sadece renge bakarak ayirt edemeyebilir.
 * Bu yuzden her turun rengi yaninda sabit bir simge de var; simge `aria-hidden` (ekran
 * okuyucu zaten `role="alert"` sayesinde mesaji okuyor, simgeyi ayrica seslendirmesine
 * gerek yok — sadece gorsel/renkli bir yedek ipucu).
 */
const KIND_ICON: Record<NotificationKind, string> = {
  success: "✓",
  conflict: "⚠",
  error: "✕",
};

/**
 * App.tsx'te tum uygulamayi sarmalayan Provider. Eskiden kendi state'inde bir bildirim yigini
 * tutup elle render ederdi (bkz. git gecmisi); simdi PrimeReact'in {@link Toast}'una sariyor —
 * {@code toastRef.current.show(...)} imperatif cagrisi Toast'un KENDI ic yiginina ekliyor,
 * ayni anda birden fazla bildirimin ust uste binmeden gosterilmesi (stacking) artik bizim
 * elle yazdigimiz bir seyi degil.
 * <p>
 * {@code content} render prop'uyla PrimeReact'in varsayilan summary/detail sablonunu HIC
 * kullanmiyoruz — bunun yerine eski markup'imizi ({@code .notification}, {@code role="alert"}
 * vb.) birebir ayni class isimleriyle kendimiz basiyoruz, boylece hem gorunum hem de mevcut
 * testlerin ({@code closest('[role="alert"]')}) sorguladigi yapi degismedi.
 */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const toastRef = useRef<Toast>(null);

  const notify = useCallback<NotifyFn>((status, message, action) => {
    const kind = kindForStatus(status);
    // toastMessage'i once bir degiskene atayip content() icinden ona referans veriyoruz —
    // "Geri Al" butonuna basilinca Toast'un show() ile verdigimiz AYNI nesneyi remove()'a
    // geçirmemiz gerekiyor (Toast, mesaji referans esitligiyle bulup kaldiriyor).
    const toastMessage: ToastMessage = {
      life: NOTIFICATION_DURATION_MS,
      // Eski tasarimda kapatma butonu hic yoktu (sadece otomatik kapanma + "Geri Al" gibi
      // ozel bir aksiyon) — PrimeReact'in varsayilan X butonunu bilerek kapatiyoruz.
      closable: false,
      content: () => (
        <div className={`notification notification-${kind}`} role="alert">
          <span className="notification-icon" aria-hidden="true">
            {KIND_ICON[kind]}
          </span>
          <span className="notification-message">{message}</span>
          {action && (
            <button
              type="button"
              className="notification-action"
              onClick={() => {
                action.onClick();
                toastRef.current?.remove(toastMessage);
              }}
            >
              {action.label}
            </button>
          )}
        </div>
      ),
    };
    toastRef.current?.show(toastMessage);
  }, []);

  return (
    <NotificationContext.Provider value={notify}>
      {children}
      {/* pt.root: Toast'in DIS kapsayicisini (sabit konumlanan asil kutu) hedefliyor — unstyled
          modda p-* class'lari hic eklenmiyor (bkz. notifications.css'teki not), o yuzden
          konumlandirmayi CSS'te ".p-toast-bottom-right" gibi tahmini bir class'a degil,
          burada acikca verdigimiz "toast-stack"e bagliyoruz. */}
      <Toast ref={toastRef} position="bottom-right" pt={{ root: { className: "toast-stack" } }} />
    </NotificationContext.Provider>
  );
}

/**
 * Diger component'lerin bildirim gostermek icin cagirdigi custom hook. NotificationProvider'in
 * disinda (Context degeri hala null iken) cagrilirsa, sessizce yanlis calismak yerine hemen
 * hata firlatir — boylece hata gelistirme sirasinda hemen fark edilir.
 */
export function useNotify(): NotifyFn {
  const notify = useContext(NotificationContext);
  if (!notify) {
    throw new Error("useNotify must be used within a NotificationProvider");
  }
  return notify;
}

/**
 * catch bloklarinda tekrar eden kalibi tek yere toplar. ApiError ise ve backend bir `code`
 * gonderdiyse, o kodu i18next'in "errors" sozlugunden cevirip (details ile interpolasyon
 * yaparak) gosterir — boylece backend'in Ingilizce mesaji degil, kullanicinin secili dili
 * ekrana cikar. `code` taninmiyorsa (defaultValue mekanizmasi sayesinde) backend'in ham
 * Ingilizce mesajina duser; ApiError hic degilse (network hatasi vs.) genel bir 500
 * bildirimi gosterilir.
 */
export function notifyFromError(
  notify: NotifyFn,
  t: TFunction,
  err: unknown,
  fallbackMessage: string
) {
  if (err instanceof ApiError) {
    const message = err.code
      ? t(`errors.${err.code}`, { ...err.details, defaultValue: err.message })
      : err.message;
    notify(err.status, message);
    return;
  }
  notify(500, fallbackMessage);
}
