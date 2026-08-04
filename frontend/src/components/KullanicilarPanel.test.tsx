import "@testing-library/jest-dom";
import "../i18n";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { PrimeReactProvider } from "primereact/api";
import { KullanicilarPanel } from "./KullanicilarPanel";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";
import { Kullanici } from "../api/kullanicilar";
import { ConfirmProvider } from "../notifications/ConfirmProvider";

const KULLANICILAR: Kullanici[] = [
  { id: 1, kullaniciAdi: "admin", rol: "ADMIN" },
  { id: 2, kullaniciAdi: "ayse", rol: "VIEWER" },
];

const ADMIN_AUTH: AuthContextValue = {
  status: "authenticated",
  kullaniciAdi: "admin",
  rol: "ADMIN",
  canWrite: true,
  isAdmin: true,
  login: jest.fn(),
  logout: jest.fn(),
};

function renderPanel(
  props: Partial<React.ComponentProps<typeof KullanicilarPanel>> = {},
  auth: AuthContextValue = ADMIN_AUTH
) {
  const defaultProps = {
    kullanicilar: KULLANICILAR,
    onCreate: jest.fn().mockResolvedValue(undefined),
    onChangeRol: jest.fn().mockResolvedValue(undefined),
    onDelete: jest.fn(),
  };
  return render(
    <PrimeReactProvider value={{ unstyled: true }}>
      <ConfirmProvider>
        <AuthContext.Provider value={auth}>
          <KullanicilarPanel {...defaultProps} {...props} />
        </AuthContext.Provider>
      </ConfirmProvider>
    </PrimeReactProvider>
  );
}

/**
 * Sadece o kullanicinin adini tasiyan <td> hucresini arar (satirin tamamini ya da rol
 * dropdown'undaki "ADMIN"/"VIEWER" gibi metinleri degil). "admin" satiri "(sen)" etiketiyle
 * birlikte "admin (sen)" seklinde render oldugu icin exact eslesme yerine startsWith kullanilir;
 * ayni sebeple case-insensitive bir { exact: false } kullanmiyoruz — o, "ADMIN" secenegiyle
 * de eslesip "birden fazla eleman bulundu" hatasina yol acardi.
 */
function row(kullaniciAdi: string) {
  return screen
    .getByText(
      (content, element) =>
        element?.tagName.toLowerCase() === "td" && content.startsWith(kullaniciAdi)
    )
    .closest("tr") as HTMLElement;
}

test("kullanicilar listelenir, giris yapan kullanici (sen) etiketiyle isaretlenir", () => {
  renderPanel();

  expect(screen.getByText("ayse")).toBeInTheDocument();
  // "admin" hem satirda hem de "(sen)" etiketiyle birlikte gorunuyor.
  expect(row("admin")).toHaveTextContent("(sen)");
  expect(row("ayse")).not.toHaveTextContent("(sen)");
});

test("hic kullanici yoksa bos liste metni gosterilir", () => {
  renderPanel({ kullanicilar: [] });

  expect(screen.getByText("Henüz kullanıcı yok")).toBeInTheDocument();
});

test("rol dropdown'i degistirmek onChangeRol'u dogru id ve rolle cagirir", () => {
  const onChangeRol = jest.fn().mockResolvedValue(undefined);
  renderPanel({ onChangeRol });

  const ayseRow = row("ayse");
  const select = within(ayseRow).getByDisplayValue("VIEWER");
  fireEvent.change(select, { target: { value: "EDITOR" } });

  expect(onChangeRol).toHaveBeenCalledWith(2, "EDITOR");
});

test("Sil'e basip onaylayinca onDelete dogru id ile cagirilir", async () => {
  const onDelete = jest.fn();
  renderPanel({ onDelete });

  fireEvent.click(within(row("ayse")).getByText("Sil"));

  // window.confirm yerine ConfirmProvider'in Promise tabanli ozel modali — modal icindeki
  // onay butonunu satirdakinden ayirt etmek icin within(modal) kullaniliyor.
  const modal = await screen.findByRole("alertdialog");
  fireEvent.click(within(modal).getByRole("button", { name: "Sil" }));

  await waitFor(() => expect(onDelete).toHaveBeenCalledWith(2));
});

test("onaylanmazsa onDelete cagrilmaz", async () => {
  const onDelete = jest.fn();
  renderPanel({ onDelete });

  fireEvent.click(within(row("ayse")).getByText("Sil"));

  const modal = await screen.findByRole("alertdialog");
  fireEvent.click(within(modal).getByRole("button", { name: "Vazgeç" }));

  expect(onDelete).not.toHaveBeenCalled();
});

test("yeni kullanici formu dogru degerlerle onCreate'i cagirir ve alanlari temizler", async () => {
  const onCreate = jest.fn().mockResolvedValue(undefined);
  renderPanel({ onCreate });

  // Sayfada birden fazla "VIEWER" degerli <select> var (ayse'nin satiri + bu form), o yuzden
  // formun kendi select'ini butonun bulundugu <form>'a gore buluyoruz.
  const form = screen.getByText("Kullanıcı Oluştur").closest("form") as HTMLElement;
  fireEvent.change(within(form).getByPlaceholderText("kullanici_adi"), {
    target: { value: "mehmet" },
  });
  fireEvent.change(within(form).getByPlaceholderText("parola"), {
    target: { value: "editorParola1" },
  });
  fireEvent.change(within(form).getByDisplayValue("VIEWER"), { target: { value: "EDITOR" } });
  fireEvent.click(within(form).getByText("Kullanıcı Oluştur"));

  expect(onCreate).toHaveBeenCalledWith("mehmet", "editorParola1", "EDITOR");
  // Alanlarin temizlenmesi onCreate'in Promise'i cozulduKTEN SONRA gelen bir state guncellemesi
  // (bkz. handleCreateSubmit'teki await), o yuzden anlik degil waitFor ile bekleniyor.
  await waitFor(() => expect(screen.getByPlaceholderText("kullanici_adi")).toHaveValue(""));
  expect(screen.getByPlaceholderText("parola")).toHaveValue("");
});
