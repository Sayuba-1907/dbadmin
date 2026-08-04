import { ReactNode, createContext, useCallback, useContext } from "react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog, confirmDialog } from "primereact/confirmdialog";
import "./confirm.css";

/** window.confirm'in yerini tutan Promise tabanli fonksiyon. Eskiden kendi yazdigimiz bir modal
 * kullanilirdi (bkz. git gecmisi); simdi PrimeReact'in confirmDialog()'una sariyoruz — o
 * callback tabanli (accept/reject) oldugu icin burada Promise'e ceviriyoruz, boylece cagiran
 * taraf ({@code await confirm(mesaj)}) hic degismedi. */
type ConfirmFn = (message: string) => Promise<boolean>;

const ConfirmContext = createContext<ConfirmFn | null>(null);

/**
 * App.tsx'te NotificationProvider'in yanina, tum uygulamayi sarmalayan ikinci bir Provider.
 * {@link ConfirmDialog} tek bir yerde (burada) render edilir — {@code confirmDialog(...)}
 * cagrisi onun icerigini/gorunurlugunu global olarak degistirir, aynen bir toast kuyrugu gibi
 * ama tek seferde sadece bir tanesi acik olabilir.
 * <p>
 * {@code pt} (passthrough) prop'lariyla PrimeReact'in kendi (bu projede hic yuklenmeyen)
 * temasi yerine mevcut {@code confirm.css}/{@code App.css} class'larimizi ({@code confirm-modal},
 * {@code btn-danger} vb.) enjekte ediyoruz — boylece gorunum onceki elle yazilmis modalla ayni
 * kaliyor, degisen sadece mekanizmanin kendisi.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();

  const confirm = useCallback<ConfirmFn>(
    (message) => {
      return new Promise<boolean>((resolve) => {
        confirmDialog({
          message,
          acceptLabel: t("common.delete"),
          rejectLabel: t("common.cancel"),
          // onHide, Escape/maskeye tiklama DAHIL her kapanma yolunda cagrilir; accept/reject
          // zaten kendi resolve'unu yapiyor olsa da Promise'in HER durumda kesin sonuclanmasini
          // garanti etmek icin buraya da bir varsayilan (false) koyduk — iki kez resolve etmek
          // (Promise'in dogasi geregi) zararsiz, sadece ilki gecerli sayilir.
          accept: () => resolve(true),
          reject: () => resolve(false),
          onHide: () => resolve(false),
          // Butonlarin class'i pt'den DEGIL, bu iki ozel prop'tan geliyor — ConfirmDialog'un
          // kaynagina bakinca (confirmdialog.cjs.js) rejectButton/acceptButton'in className'i
          // pt.rejectButton/pt.acceptButton'dan degil dogrudan bu iki prop'tan kuruluyor.
          rejectClassName: "btn",
          acceptClassName: "btn btn-danger",
          // root/footer duz HTML elemanlari oldugu icin pt {className} burada calisiyor.
          // "message" ise CSS'ten (confirm.css, data-pc-section="message" secici) hedefleniyor.
          pt: {
            // role: PrimeReact varsayilan olarak "dialog" veriyor, biz "alertdialog" istiyoruz
            // (kullanicinin cevaplamadan gecemeyecegi bir onay penceresi oldugunu ekran
            // okuyuculara daha dogru anlatiyor — eski elle yazdigimiz modalda da boyleydi).
            root: { className: "confirm-modal", role: "alertdialog" },
            footer: { className: "confirm-actions" },
          },
        });
      });
    },
    [t]
  );

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <ConfirmDialog />
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
