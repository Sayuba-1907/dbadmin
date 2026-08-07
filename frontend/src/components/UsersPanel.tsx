import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Password } from "primereact/password";
import { Button } from "primereact/button";
import { User } from "../api/users";
import { Role } from "../api/auth";
import { useAuth } from "../auth/AuthProvider";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";
import { useConfirm } from "../notifications/ConfirmProvider";

const ROLES: Role[] = ["VIEWER", "EDITOR", "ADMIN"];

interface UsersPanelProps {
  users: User[];
  onCreate: (username: string, password: string, role: Role) => Promise<void>;
  onChangeRole: (id: number, role: Role) => Promise<void>;
  onDelete: (id: number) => void;
}

/**
 * "Kullanıcılar" gorunumu — sadece ADMIN'e acik (bkz. WorkspaceNav, SecurityConfig). User
 * listesi + role degistirme + yeni kullanici olusturma + silme, hepsi tek ekranda.
 * <p>
 * Role degisimi role secilir secilmez ANINDA API'ye gider (TableDetail'deki "taslak biriktir,
 * Kaydet'e basinca gonder" deseninin aksine) — burada birden fazla alani birlikte degistirip
 * tek seferde kaydetme ihtiyaci yok, her kullanici satiri bagimsiz bir aksiyon.
 */
export function UsersPanel({ users, onCreate, onChangeRole, onDelete }: UsersPanelProps) {
  const { t } = useTranslation();
  const { username: myUsername } = useAuth();
  const confirm = useConfirm();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("VIEWER");

  async function handleCreateSubmit(event: FormEvent) {
    event.preventDefault();
    await onCreate(username, password, role);
    setUsername("");
    setPassword("");
    setRole("VIEWER");
  }

  return (
    <section className="users-panel fadeinup animation-duration-200">
      <h2>{t("kullanicilar.title")}</h2>
      <DataTable
        value={users}
        dataKey="id"
        className="user-table w-full"
        emptyMessage={t("kullanicilar.empty")}
      >
        <Column
          field="username"
          header={t("kullanicilar.colUsername")}
          sortable
          body={(user: User) => (
            <>
              {user.username}
              {user.username === myUsername && (
                <span className="you-badge"> ({t("kullanicilar.you")})</span>
              )}
            </>
          )}
        />
        <Column
          field="role"
          header={t("kullanicilar.colRole")}
          sortable
          body={(user: User) => (
            <>
              <span className={`role-badge inline-block role-badge-${user.role.toLowerCase()}`}>
                {user.role}
              </span>
              <select
                value={user.role}
                onChange={(e) => onChangeRole(user.id, e.target.value as Role)}
              >
                {ROLES.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </>
          )}
        />
        <Column
          body={(user: User) => (
            <button
              className="btn btn-link btn-danger"
              onClick={async () => {
                if (await confirm(t("kullanicilar.confirmDelete", { name: user.username }))) {
                  onDelete(user.id);
                }
              }}
            >
              {t("common.delete")}
            </button>
          )}
        />
      </DataTable>

      <form className="create-user-form flex align-items-center" onSubmit={handleCreateSubmit}>
        <InputText
          type="text"
          placeholder={t("kullanicilar.usernamePlaceholder")}
          value={username}
          onChange={(e) => {
            clearCustomValidity(e);
            setUsername(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          required
        />
        <Password
          className="user-password relative"
          placeholder={t("kullanicilar.passwordPlaceholder")}
          value={password}
          onChange={(e) => {
            clearCustomValidity(e);
            setPassword(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          feedback={false}
          toggleMask
          required
        />
        <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
        <Button className="btn btn-primary" type="submit" label={t("kullanicilar.create")} />
      </form>
    </section>
  );
}
