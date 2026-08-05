import { FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Password } from "primereact/password";
import { Button } from "primereact/button";
import { Kullanici } from "../api/kullanicilar";
import { Rol } from "../api/auth";
import { useAuth } from "../auth/AuthProvider";
import { clearCustomValidity, onRequiredInvalid } from "../i18n/nativeValidation";
import { useConfirm } from "../notifications/ConfirmProvider";

const ROLLER: Rol[] = ["VIEWER", "EDITOR", "ADMIN"];

interface KullanicilarPanelProps {
  kullanicilar: Kullanici[];
  onCreate: (kullaniciAdi: string, parola: string, rol: Rol) => Promise<void>;
  onChangeRol: (id: number, rol: Rol) => Promise<void>;
  onDelete: (id: number) => void;
}

/**
 * "Kullanıcılar" gorunumu — sadece ADMIN'e acik (bkz. WorkspaceNav, SecurityConfig). Kullanici
 * listesi + rol degistirme + yeni kullanici olusturma + silme, hepsi tek ekranda.
 * <p>
 * Rol degisimi rol secilir secilmez ANINDA API'ye gider (TabloDetail'deki "taslak biriktir,
 * Kaydet'e basinca gonder" deseninin aksine) — burada birden fazla alani birlikte degistirip
 * tek seferde kaydetme ihtiyaci yok, her kullanici satiri bagimsiz bir aksiyon.
 */
export function KullanicilarPanel({
  kullanicilar,
  onCreate,
  onChangeRol,
  onDelete,
}: KullanicilarPanelProps) {
  const { t } = useTranslation();
  const { kullaniciAdi: benimKullaniciAdim } = useAuth();
  const confirm = useConfirm();
  const [kullaniciAdi, setKullaniciAdi] = useState("");
  const [parola, setParola] = useState("");
  const [rol, setRol] = useState<Rol>("VIEWER");

  async function handleCreateSubmit(event: FormEvent) {
    event.preventDefault();
    await onCreate(kullaniciAdi, parola, rol);
    setKullaniciAdi("");
    setParola("");
    setRol("VIEWER");
  }

  return (
    <section className="kullanicilar-panel fadeinup animation-duration-200">
      <h2>{t("kullanicilar.title")}</h2>
      <DataTable
        value={kullanicilar}
        dataKey="id"
        className="kullanici-table w-full"
        emptyMessage={t("kullanicilar.empty")}
      >
        <Column
          field="kullaniciAdi"
          header={t("kullanicilar.colUsername")}
          sortable
          body={(kullanici: Kullanici) => (
            <>
              {kullanici.kullaniciAdi}
              {kullanici.kullaniciAdi === benimKullaniciAdim && (
                <span className="you-badge"> ({t("kullanicilar.you")})</span>
              )}
            </>
          )}
        />
        <Column
          field="rol"
          header={t("kullanicilar.colRole")}
          sortable
          body={(kullanici: Kullanici) => (
            <>
              <span className={`role-badge inline-block role-badge-${kullanici.rol.toLowerCase()}`}>
                {kullanici.rol}
              </span>
              <select
                value={kullanici.rol}
                onChange={(e) => onChangeRol(kullanici.id, e.target.value as Rol)}
              >
                {ROLLER.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </>
          )}
        />
        <Column
          body={(kullanici: Kullanici) => (
            <button
              className="btn btn-link btn-danger"
              onClick={async () => {
                if (
                  await confirm(t("kullanicilar.confirmDelete", { name: kullanici.kullaniciAdi }))
                ) {
                  onDelete(kullanici.id);
                }
              }}
            >
              {t("common.delete")}
            </button>
          )}
        />
      </DataTable>

      <form className="create-kullanici-form flex align-items-center" onSubmit={handleCreateSubmit}>
        <InputText
          type="text"
          placeholder={t("kullanicilar.usernamePlaceholder")}
          value={kullaniciAdi}
          onChange={(e) => {
            clearCustomValidity(e);
            setKullaniciAdi(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          required
        />
        <Password
          className="kullanici-password relative"
          placeholder={t("kullanicilar.passwordPlaceholder")}
          value={parola}
          onChange={(e) => {
            clearCustomValidity(e);
            setParola(e.target.value);
          }}
          onInvalid={onRequiredInvalid(t)}
          feedback={false}
          toggleMask
          required
        />
        <select value={rol} onChange={(e) => setRol(e.target.value as Rol)}>
          {ROLLER.map((r) => (
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
