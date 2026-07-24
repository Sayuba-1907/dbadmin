import "@testing-library/jest-dom";
import "../i18n";
import i18n from "../i18n";
import { render, screen, fireEvent } from "@testing-library/react";
import { useTranslation } from "react-i18next";
import { LanguageSwitcher } from "./LanguageSwitcher";

// Her testten sonra dili varsayilana (tr) dondur — i18n singleton tum test dosyasi
// boyunca ayni oldugu icin, bir testte degistirilen dil temizlenmezse sonraki testi bozar.
afterEach(() => {
  i18n.changeLanguage("tr");
});

/** Gercek bir cevrilmis metnin dile gore degisip degismedigini gozlemlemek icin kucuk bir prob component. */
function TranslatedProbe() {
  const { t } = useTranslation();
  return <p>{t("sidebar.newTable")}</p>;
}

function renderSwitcher() {
  return render(
    <>
      <LanguageSwitcher />
      <TranslatedProbe />
    </>
  );
}

test("EN butonuna tiklayinca uygulama genelindeki cevrilmis metin de Ingilizce'ye doner", () => {
  renderSwitcher();
  expect(screen.getByText("+ Yeni Tablo")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "EN" }));

  expect(screen.getByText("+ New Table")).toBeInTheDocument();
  expect(screen.queryByText("+ Yeni Tablo")).not.toBeInTheDocument();
});

test("aktif dilin butonu disabled ve 'active' class'ini tasir", () => {
  renderSwitcher();

  expect(screen.getByRole("button", { name: "TR" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "TR" })).toHaveClass("active");
  expect(screen.getByRole("button", { name: "EN" })).not.toBeDisabled();
});

test("secilen dil localStorage'a yazilir (sayfa yenilense de kalici olsun diye)", () => {
  renderSwitcher();

  fireEvent.click(screen.getByRole("button", { name: "EN" }));

  expect(localStorage.getItem("dbadmin-language")).toBe("en");
});
