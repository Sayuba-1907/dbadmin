import { FormEvent, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { useAuth } from "../auth/AuthProvider";
import {
  changePassword,
  fetchAvatarObjectUrl,
  removeAvatar,
  updateProfile,
  uploadAvatar,
} from "../api/auth";
import { notifyFromError, useNotify } from "../notifications/NotificationProvider";

const ALLOWED_AVATAR_TYPES = ["image/png", "image/jpeg", "image/webp"];

/**
 * "Profil" görünümü — herkese açık (WorkspaceNav'da isAdmin şartı yok, bkz. o dosyanın notu):
 * her kullanıcı SADECE kendi profiline bakar/düzenler, backend de {@code authentication.getName()}
 * üzerinden çalıştığı için (path'te id yok) burada ayrıca bir yetki kontrolüne gerek yok.
 * <p>
 * Üç bağımsız form — biri patlarsa diğerleri etkilenmez: ad soyad/kullanıcı adı, parola, avatar.
 * Kullanıcı adı değişince backend TAZE bir token döner (bkz. AuthController#updateProfile
 * javadoc'u); {@link useAuth}'un {@code applyProfileUpdate}'i bunu sessizce localStorage'a yazar,
 * kullanıcı tekrar giriş yapmak zorunda kalmaz.
 */
export function ProfilePanel() {
  const { t } = useTranslation();
  const notify = useNotify();
  const { username, fullName, email, hasAvatar, applyProfileUpdate } = useAuth();

  const [fullNameInput, setFullNameInput] = useState(fullName ?? "");
  const [usernameInput, setUsernameInput] = useState(username ?? "");
  const [emailInput, setEmailInput] = useState(email ?? "");
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);

  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [avatarLightboxOpen, setAvatarLightboxOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Sekmeye her girişte (ve avatar değiştiğinde, bkz. handleAvatarChange) taze bir object URL
  // çekilir — önceki URL varsa serbest bırakılır, tarayıcı belleğinde birikmesin diye.
  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;
    if (hasAvatar) {
      fetchAvatarObjectUrl().then((url) => {
        if (cancelled) {
          if (url) {
            URL.revokeObjectURL(url);
          }
          return;
        }
        objectUrl = url;
        setAvatarUrl(url);
      });
    } else {
      setAvatarUrl(null);
    }
    return () => {
      cancelled = true;
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [hasAvatar]);

  useEffect(() => {
    if (!avatarLightboxOpen) {
      return;
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setAvatarLightboxOpen(false);
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [avatarLightboxOpen]);

  async function handleProfileSubmit(event: FormEvent) {
    event.preventDefault();
    setSavingProfile(true);
    try {
      const result = await updateProfile(
        fullNameInput.trim() || null,
        usernameInput.trim(),
        emailInput.trim() || null
      );
      applyProfileUpdate(result);
      notify(200, t("profile.profileSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("profile.profileSaveFailed"));
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      notify(400, t("profile.passwordMismatch"));
      return;
    }
    setSavingPassword(true);
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      notify(200, t("profile.passwordSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("profile.passwordSaveFailed"));
    } finally {
      setSavingPassword(false);
    }
  }

  async function handleAvatarChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) {
      return;
    }
    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      notify(400, t("profile.avatarInvalidType"));
      return;
    }
    setUploadingAvatar(true);
    try {
      const result = await uploadAvatar(file);
      applyProfileUpdate(result);
      notify(200, t("profile.avatarSaved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("profile.avatarSaveFailed"));
    } finally {
      setUploadingAvatar(false);
    }
  }

  async function handleRemoveAvatar() {
    setUploadingAvatar(true);
    try {
      const result = await removeAvatar();
      applyProfileUpdate(result);
      notify(200, t("profile.avatarRemoved"));
    } catch (err) {
      notifyFromError(notify, t, err, t("profile.avatarRemoveFailed"));
    } finally {
      setUploadingAvatar(false);
    }
  }

  return (
    <section className="profile-panel fadeinup animation-duration-200">
      <div className="maintenance-header flex align-items-center justify-content-between">
        <h2>{t("profile.title")}</h2>
      </div>

      <div className="profile-avatar-card detail-card">
        <h3>{t("profile.avatarTitle")}</h3>
        <div className="profile-avatar-row flex align-items-center">
          <button
            type="button"
            className={`profile-avatar-preview${avatarUrl ? " profile-avatar-preview-clickable" : ""}`}
            // Placeholder harfe tiklamanin bir anlami yok (buyutulecek gercek bir foto yok),
            // sadece gercek bir avatar varken lightbox acilsin.
            onClick={avatarUrl ? () => setAvatarLightboxOpen(true) : undefined}
            aria-label={avatarUrl ? t("profile.viewAvatar") : undefined}
          >
            {avatarUrl ? (
              <img src={avatarUrl} alt="" className="profile-avatar-img" />
            ) : (
              <span className="profile-avatar-placeholder">
                {(fullName || username || "?").charAt(0).toUpperCase()}
              </span>
            )}
          </button>
          <div className="profile-avatar-actions">
            <input
              ref={fileInputRef}
              type="file"
              accept={ALLOWED_AVATAR_TYPES.join(",")}
              className="profile-avatar-file-input"
              onChange={handleAvatarChange}
            />
            <div className="profile-avatar-buttons">
              <Button
                className="btn btn-secondary"
                label={t("profile.changeAvatar")}
                loading={uploadingAvatar}
                onClick={() => fileInputRef.current?.click()}
              />
              {hasAvatar && (
                <button
                  type="button"
                  className="btn btn-link btn-danger"
                  disabled={uploadingAvatar}
                  onClick={handleRemoveAvatar}
                >
                  {t("profile.removeAvatar")}
                </button>
              )}
            </div>
            <p className="profile-avatar-hint">{t("profile.avatarHint")}</p>
          </div>
        </div>
      </div>

      <div className="profile-info-card detail-card">
        <h3>{t("profile.infoTitle")}</h3>
        <form className="profile-form" onSubmit={handleProfileSubmit}>
          <label className="profile-field">
            <span>{t("profile.fullName")}</span>
            <InputText
              value={fullNameInput}
              onChange={(e) => setFullNameInput(e.target.value)}
              placeholder={t("profile.fullNamePlaceholder")}
            />
          </label>
          <label className="profile-field">
            <span>{t("profile.username")}</span>
            <InputText value={usernameInput} onChange={(e) => setUsernameInput(e.target.value)} />
          </label>
          <label className="profile-field">
            <span>{t("profile.email")}</span>
            <InputText
              type="email"
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              placeholder={t("profile.emailPlaceholder")}
            />
          </label>
          <Button
            className="btn btn-primary"
            type="submit"
            label={t("common.save")}
            loading={savingProfile}
          />
        </form>
      </div>

      <div className="profile-password-card detail-card">
        <h3>{t("profile.passwordTitle")}</h3>
        <form className="profile-form" onSubmit={handlePasswordSubmit}>
          <label className="profile-field">
            <span>{t("profile.currentPassword")}</span>
            <InputText
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>
          <label className="profile-field">
            <span>{t("profile.newPassword")}</span>
            <InputText
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
            />
          </label>
          <label className="profile-field">
            <span>{t("profile.confirmPassword")}</span>
            <InputText
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
            />
          </label>
          <Button
            className="btn btn-primary"
            type="submit"
            label={t("profile.changePassword")}
            loading={savingPassword}
          />
        </form>
      </div>

      {avatarLightboxOpen && avatarUrl && (
        // Overlay'in KENDISINE tiklamak kapatir (button'a tiklamak stopPropagation ile
        // engellenir) — CreateSchemaForm/CreateTableForm'daki .modal-overlay deseniyle ayni.
        <div
          className="modal-overlay fixed flex align-items-center justify-content-center"
          onClick={() => setAvatarLightboxOpen(false)}
        >
          <img
            src={avatarUrl}
            alt=""
            className="profile-avatar-lightbox-img"
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </section>
  );
}
