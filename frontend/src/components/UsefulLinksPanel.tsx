import { Fragment } from "react";
import { useTranslation } from "react-i18next";

/**
 * docker-compose.yml'deki sabit host-port eslemeleri (8081/3001/9090) — hostname'i sabit
 * "localhost" yazmak yerine sayfanin kendi hostname'inden turetiyoruz, boylece frontend
 * localhost disinda (mesela LAN IP'siyle) acildiginda da linkler dogru adrese gider.
 */
function adminLinkUrl(port: number, path = "") {
  return `http://${window.location.hostname}:${port}${path}`;
}

/**
 * Iki grup: biri backend'in kendi gozlemlenebilirligi/API'i (Swagger/Grafana/Prometheus), digeri
 * backend'in yaslandigi altyapi servisleri (MinIO/RedisInsight/RabbitMQ) — hepsi tek sirada yan
 * yana dururken (3 -> 6 karta cikinca) anlamsiz bir duvar gibi gorunuyordu, gruplamak taramayi
 * kolaylastiriyor.
 */
const ADMIN_LINK_GROUPS = [
  {
    key: "observability",
    links: [
      { key: "swagger", url: adminLinkUrl(8081, "/swagger-ui.html"), icon: "⌘" },
      { key: "grafana", url: adminLinkUrl(3001), icon: "▤" },
      { key: "prometheus", url: adminLinkUrl(9090), icon: "▲" },
    ],
  },
  {
    key: "infra",
    links: [
      // Bu ucun kendi web konsolu var (bkz. docker-compose.yml): giris sayfasinda
      // MINIO_ROOT_USER/MINIO_ROOT_PASSWORD (.env) istenir.
      { key: "minio", url: adminLinkUrl(9001), icon: "▦" },
      // RedisInsight, Redis'in kendisi degil onu gorsellestiren ayri bir container (bkz.
      // docker-compose.yml notu) — ilk acilista "Add Redis database" ekraninda host=redis,
      // port=6379 girilmesi gerekir, sifre yok.
      { key: "redis", url: adminLinkUrl(5540), icon: "◆" },
      // rabbitmq:3-management-alpine image'inin kendi UI'i — RABBITMQ_USER/RABBITMQ_PASSWORD (.env).
      { key: "rabbitmq", url: adminLinkUrl(15672), icon: "▥" },
    ],
  },
] as const;

/**
 * Requirement notu 9 ("Ayarlar Sayfası") — hesap popup'undaki "Ayarlar" flyout'unun "Faydalı
 * Linkler" secenegi (bkz. WorkspaceNav): Swagger UI/Grafana/Prometheus/MinIO/RedisInsight/
 * RabbitMQ, sadece ADMIN'e acik. Kendi verisi yok, tamamen statik — bu yuzden Dashboard'dan
 * hicbir prop almiyor.
 */
export function UsefulLinksPanel() {
  const { t } = useTranslation();

  return (
    <section className="settings-panel fadeinup animation-duration-200">
      <div className="settings-header flex align-items-center justify-content-between">
        <h2>{t("settings.adminLinksTitle")}</h2>
      </div>

      {ADMIN_LINK_GROUPS.map((group, index) => (
        <Fragment key={group.key}>
          {index > 0 && <div className="settings-admin-divider" />}
          <div>
            <h3 className="settings-admin-links-title">{t(`settings.linkGroup.${group.key}`)}</h3>
            <div className="settings-admin-links flex">
              {group.links.map((link) => (
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
          </div>
        </Fragment>
      ))}
    </section>
  );
}
