import "@testing-library/jest-dom";
import "../i18n";
import { render, screen } from "@testing-library/react";
import { WorkspaceNav } from "./WorkspaceNav";
import { AuthContext, AuthContextValue } from "../auth/AuthProvider";

function renderNav(role: AuthContextValue["role"]) {
  const auth: AuthContextValue = {
    status: "authenticated",
    id: 1,
    username: "test",
    fullName: null,
    hasAvatar: false,
    role,
    canWrite: role === "EDITOR" || role === "ADMIN",
    isAdmin: role === "ADMIN",
    login: jest.fn(),
    logout: jest.fn(),
    applyProfileUpdate: jest.fn(),
  };
  return render(
    <AuthContext.Provider value={auth}>
      <WorkspaceNav active="schemas" onChange={jest.fn()} />
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
