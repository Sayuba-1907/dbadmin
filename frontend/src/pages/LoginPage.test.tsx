import "@testing-library/jest-dom";
import "../i18n";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { LoginPage } from "./LoginPage";
import { NotificationProvider } from "../notifications/NotificationProvider";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";
import { ApiError } from "../api/client";

function renderLoginPage(login: AuthContextValue["login"]) {
  const auth: AuthContextValue = {
    status: "anonymous",
    username: null,
    role: null,
    canWrite: false,
    isAdmin: false,
    login,
    logout: jest.fn(),
  };
  return render(
    <NotificationProvider>
      <AuthContext.Provider value={auth}>
        <LoginPage />
      </AuthContext.Provider>
    </NotificationProvider>
  );
}

test("kullanici adi ve parola girip gonderince login dogru degerlerle cagrilir", async () => {
  const login = jest.fn().mockResolvedValue(undefined);
  renderLoginPage(login);

  fireEvent.change(screen.getByLabelText("Kullanıcı adı"), { target: { value: "admin" } });
  fireEvent.change(screen.getByLabelText("Parola"), { target: { value: "admin123" } });
  fireEvent.click(screen.getByRole("button", { name: "Giriş Yap" }));

  await waitFor(() => expect(login).toHaveBeenCalledWith("admin", "admin123"));
});

test("yanlis kullanici adi/parola bildirim olarak gosterilir", async () => {
  const login = jest
    .fn()
    .mockRejectedValue(
      new ApiError(401, "invalid username or password", "AUTH_INVALID_CREDENTIALS")
    );
  renderLoginPage(login);

  fireEvent.change(screen.getByLabelText("Kullanıcı adı"), { target: { value: "admin" } });
  fireEvent.change(screen.getByLabelText("Parola"), { target: { value: "yanlis" } });
  fireEvent.click(screen.getByRole("button", { name: "Giriş Yap" }));

  const notificationText = await screen.findByText("invalid username or password");
  expect(notificationText.closest('[role="alert"]')).toHaveClass("notification-error");
});

test("istek surerken buton 'Giriş yapılıyor...' metnine doner ve devre disi kalir", async () => {
  let resolveLogin!: () => void;
  const login = jest.fn(() => new Promise<void>((resolve) => (resolveLogin = resolve)));
  renderLoginPage(login);

  fireEvent.change(screen.getByLabelText("Kullanıcı adı"), { target: { value: "admin" } });
  fireEvent.change(screen.getByLabelText("Parola"), { target: { value: "admin123" } });
  fireEvent.click(screen.getByRole("button", { name: "Giriş Yap" }));

  expect(screen.getByRole("button", { name: "Giriş yapılıyor..." })).toBeDisabled();

  resolveLogin();
  await waitFor(() => expect(screen.getByRole("button", { name: "Giriş Yap" })).not.toBeDisabled());
});
