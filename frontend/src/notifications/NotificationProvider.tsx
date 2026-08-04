import { createContext, ReactNode, useCallback, useContext, useState } from "react";
import { TFunction } from "i18next";
import { ApiError } from "../api/client";
import "./notifications.css";

type NotificationKind = "success" | "conflict" | "error";

/** Bildirimin icine konabilecek tek bir aksiyon butonu (mesela "Geri Al"). */
interface NotificationAction {
  label: string;
  onClick: () => void;
}

interface Notification {
  kind: NotificationKind;
  message: string;
  action?: NotificationAction;
}

/** Ekranda ayni anda gosterilen bir toast — id, art arda gelen bildirimleri React'in key'iyle
 * ayirt edebilmek icin (array index yeterli olmazdi: ortadan biri kaybolunca digerlerinin
 * index'i kayar). Negatif olmayan artan bir sayac, Dashboard'daki draft-kolon id deseniyle ayni. */
interface ActiveNotification extends Notification {
  id: number;
  leaving: boolean;
}

let nextNotificationId = 0;

/** action opsiyonel: cogu bildirim (basari/hata) sadece bilgilendirir, sadece geri alinabilir islemler bir aksiyon tasir. */
type NotifyFn = (status: number, message: string, action?: NotificationAction) => void;

/**
 * Bildirimin ekranda kalma suresi — geri alinabilir bir islemde bu ayni zamanda "geri alma
 * penceresi"dir. Export ediliyor ki mesela Dashboard'daki gecikmeli silme, gercek API cagrisini
 * tam bu bildirim kaybolurken tetikleyecek sekilde ayni degeri kullanabilsin.
 */
export const NOTIFICATION_DURATION_MS = 5000;

/** Toast'in kayarak kaybolma animasyonunun suresi — notifications.css'teki toast-out keyframe'iyle
 * ayni sureyi paylasir, DOM'dan asil kaldirma bu sure kadar geciktirilir ki animasyon tamamlansin. */
const EXIT_DURATION_MS = 200;

/**
 * React Context: "prop drilling" olmadan (her component'e tek tek notify fonksiyonunu
 * elle gecirmeden) agacin herhangi bir yerinden {@link useNotify} ile bu fonksiyona
 * erisilmesini saglar. Baslangic degeri null — cunku Provider disinda hic kimse bunu
 * kullanmamali (bkz. useNotify'daki hata kontrolu).
 */
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
 * App.tsx'te tum uygulamayi sarmalayan Provider. Kendi state'inde (aktif bildirim) tutar ve
 * bunu Context uzerinden altindaki her component'e acar. children (sarmaladigi tum uygulama)
 * ile birlikte, varsa aktif bildirim kutusunu da ekranin bir kosesinde render eder.
 */
export function NotificationProvider({ children }: { children: ReactNode }) {
  // Tek bir bildirim yerine bir yigin (stack): art arda gelen bildirimler birbirini kesmeden
  // alt alta birikir, her biri kendi NOTIFICATION_DURATION_MS'ini bagimsiz isletir.
  const [notifications, setNotifications] = useState<ActiveNotification[]>([]);

  // id'ye gore cikis animasyonunu baslatir, EXIT_DURATION_MS sonra o tek toast'i yigindan
  // gercekten kaldirir — digerlerine dokunmaz.
  const dismiss = useCallback((id: number) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, leaving: true } : n)));
    setTimeout(() => {
      setNotifications((prev) => prev.filter((n) => n.id !== id));
    }, EXIT_DURATION_MS);
  }, []);

  // useCallback: notify fonksiyonunun her render'da yeniden olusturulmamasini saglar
  // (referans stabil kalir), boylece Context.Provider'a verilen value her seferinde
  // "degismis" gibi algilanip alt component'lerin gereksiz yere yeniden render olmasina yol acmaz.
  const notify = useCallback<NotifyFn>(
    (status, message, action) => {
      const id = nextNotificationId++;
      setNotifications((prev) => [
        ...prev,
        { id, kind: kindForStatus(status), message, action, leaving: false },
      ]);
      // Her toast kendi zamanlayicisini kurar (tek paylasilan timer yerine) — biri erken
      // kapanirsa digerlerini etkilemez. Cikis animasyonu gercek kaldirmadan EXIT_DURATION_MS
      // once baslasin diye sure oradan dusuluyor.
      setTimeout(() => dismiss(id), Math.max(0, NOTIFICATION_DURATION_MS - EXIT_DURATION_MS));
    },
    [dismiss]
  );

  return (
    <NotificationContext.Provider value={notify}>
      {children}
      {notifications.length > 0 && (
        <div className="notification-stack">
          {notifications.map((notification) => (
            <div
              key={notification.id}
              className={`notification notification-${notification.kind}${notification.leaving ? " notification-leaving" : ""}`}
              role="alert"
            >
              <span className="notification-icon" aria-hidden="true">
                {KIND_ICON[notification.kind]}
              </span>
              <span className="notification-message">{notification.message}</span>
              {notification.action && (
                <button
                  type="button"
                  className="notification-action"
                  onClick={() => {
                    notification.action!.onClick();
                    dismiss(notification.id);
                  }}
                >
                  {notification.action.label}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
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
