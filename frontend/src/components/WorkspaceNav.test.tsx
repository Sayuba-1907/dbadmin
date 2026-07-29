import "@testing-library/jest-dom";
import "../i18n";
import { render, screen } from "@testing-library/react";
import { WorkspaceNav } from "./WorkspaceNav";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";

function renderNav(rol: AuthContextValue["rol"]) {
  const auth: AuthContextValue = {
    status: "authenticated",
    kullaniciAdi: "test",
    rol,
    canWrite: rol === "EDITOR" || rol === "ADMIN",
    isAdmin: rol === "ADMIN",
    login: jest.fn(),
    logout: jest.fn(),
  };
  return render(
    <AuthContext.Provider value={auth}>
      <WorkspaceNav active="schemalar" onChange={jest.fn()} />
    </AuthContext.Provider>
  );
}

test("ADMIN icin Kullanicilar sekmesi gorunur", () => {
  renderNav("ADMIN");

  expect(screen.getByText("Kullanıcılar")).toBeInTheDocument();
});

test("VIEWER ve EDITOR icin Kullanicilar sekmesi hic render edilmez", () => {
  renderNav("VIEWER");
  expect(screen.queryByText("Kullanıcılar")).not.toBeInTheDocument();

  renderNav("EDITOR");
  expect(screen.queryByText("Kullanıcılar")).not.toBeInTheDocument();
});
