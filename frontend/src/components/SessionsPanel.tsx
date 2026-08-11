import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "primereact/button";
import { SessionInfo, getSessions, revokeOtherSessions, revokeSession } from "../api/auth";
import { useConfirm } from "../notifications/ConfirmProvider";
import { notifyFromError, useNotify } from "../notifications/NotificationProvider";

/**
 * Requirement notu 9 ("Ayarlar Sayfası") — kullanicinin geri bildirimiyle "Ayarlar" artik tek bir
 * sayfa DEGIL, hesap popup'unda acilan bir flyout (bkz. WorkspaceNav): "Oturumlar" ve "Yönetim"
 * kendi ayri sayfalari. Bu, o flyout'un "Oturumlar" secenegi — herkese acik (kendi oturumlarin).
 */
export function SessionsPanel() {
  const { t } = useTranslation();
  const confirm = useConfirm();
  const notify = useNotify();
  const [sessions, setSessions] = useState<SessionInfo[] | null>(null);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [revokingJti, setRevokingJti] = useState<string | null>(null);
  const [revokingOthers, setRevokingOthers] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoadingSessions(true);
    getSessions()
      .then((result) => {
        if (!cancelled) {
          setSessions(result);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          notifyFromError(notify, t, err, t("settings.sessionsLoadFailed"));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSessions(false);
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleRevokeSession(jti: string) {
    setRevokingJti(jti);
    try {
      await revokeSession(jti);
      setSessions((prev) => (prev ? prev.filter((session) => session.jti !== jti) : prev));
      notify(204, t("settings.sessionRevoked"));
    } catch (err) {
      notifyFromError(notify, t, err, t("settings.sessionRevokeFailed"));
    } finally {
      setRevokingJti(null);
    }
  }

  async function handleRevokeOthers() {
    if (!(await confirm(t("settings.sessionRevokeOthersConfirm")))) {
      return;
    }
    setRevokingOthers(true);
    try {
      await revokeOtherSessions();
      setSessions((prev) => (prev ? prev.filter((session) => session.current) : prev));
      notify(204, t("settings.sessionRevoked"));
    } catch (err) {
      notifyFromError(notify, t, err, t("settings.sessionRevokeFailed"));
    } finally {
      setRevokingOthers(false);
    }
  }

  return (
    <section className="settings-panel fadeinup animation-duration-200">
      <div className="settings-header flex align-items-center justify-content-between">
        <h2>{t("settings.tabSessions")}</h2>
      </div>

      <div className="detail-card">
        <div className="settings-sessions-toolbar flex align-items-center justify-content-between">
          <span className="table-data-total-count">
            {sessions ? t("settings.sessionsTotal", { count: sessions.length }) : ""}
          </span>
          <Button
            className="btn btn-secondary"
            type="button"
            label={t("settings.sessionRevokeOthers")}
            loading={revokingOthers}
            disabled={!sessions || sessions.length <= 1}
            onClick={handleRevokeOthers}
          />
        </div>
        <table className="table-data-table w-full">
          <thead>
            <tr>
              <th>{t("settings.sessionIssuedAt")}</th>
              <th>{t("settings.sessionUserAgent")}</th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {loadingSessions && (
              <tr>
                <td colSpan={4}>{t("settings.sessionsLoading")}</td>
              </tr>
            )}
            {!loadingSessions && sessions?.length === 0 && (
              <tr>
                <td colSpan={4}>{t("settings.sessionsEmpty")}</td>
              </tr>
            )}
            {!loadingSessions &&
              sessions?.map((session) => (
                <tr key={session.jti}>
                  <td>{new Date(session.issuedAt).toLocaleString()}</td>
                  <td className="table-data-text">{session.userAgent ?? "—"}</td>
                  <td>
                    {session.current && (
                      <span className="table-data-bool table-data-bool-true">
                        {t("settings.sessionCurrentBadge")}
                      </span>
                    )}
                  </td>
                  <td>
                    <Button
                      className="btn btn-danger btn-sm"
                      type="button"
                      label={t("settings.sessionRevoke")}
                      loading={revokingJti === session.jti}
                      onClick={() => handleRevokeSession(session.jti)}
                    />
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
