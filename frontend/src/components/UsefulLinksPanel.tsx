import { useTranslation } from "react-i18next";

const ADMIN_LINKS = [
  { key: "swagger", url: "http://localhost:8081/swagger-ui.html", icon: "⌘" },
  { key: "grafana", url: "http://localhost:3001", icon: "▤" },
  { key: "prometheus", url: "http://localhost:9090", icon: "▲" },
] as const;

/**
 * Requirement notu 9 ("Ayarlar Sayfası") — hesap popup'undaki "Ayarlar" flyout'unun "Faydalı
 * Linkler" secenegi (bkz. WorkspaceNav): Swagger UI/Grafana/Prometheus, sadece ADMIN'e acik.
 * Kendi verisi yok, tamamen statik — bu yuzden Dashboard'dan hicbir prop almiyor.
 */
export function UsefulLinksPanel() {
  const { t } = useTranslation();

  return (
    <section className="settings-panel fadeinup animation-duration-200">
      <div className="settings-header flex align-items-center justify-content-between">
        <h2>{t("settings.adminLinksTitle")}</h2>
      </div>

      <div className="settings-admin-links flex">
        {ADMIN_LINKS.map((link) => (
          <a
            key={link.key}
            href={link.url}
            target="_blank"
            rel="noreferrer"
            className="summary-card summary-card-link"
          >
            <span className="summary-card-icon" aria-hidden="true">
              {link.icon}
            </span>
            <div className="summary-card-body">
              <span className="summary-card-value">{t(`settings.${link.key}LinkTitle`)}</span>
              <span className="summary-card-label">{t(`settings.${link.key}LinkDesc`)}</span>
            </div>
          </a>
        ))}
      </div>
    </section>
  );
}
