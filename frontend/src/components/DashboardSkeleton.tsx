/**
 * Dashboard ilk kez acilirken (workspace verisi henuz gelmeden) gerceginin yerini tutan
 * iskelet gorunum. Duz "Yukleniyor..." metni yerine gercek layout'un (nav + sidebar + detay
 * paneli) boyutlarini taklit ediyor ki veri gelince sayfa siçramasin.
 */
export function DashboardSkeleton() {
  return (
    <div className="dashboard" aria-busy="true">
      <div className="workspace-nav">
        <div className="skeleton skeleton-nav-btn" />
        <div className="skeleton skeleton-nav-btn" />
        <div className="skeleton skeleton-nav-btn" />
      </div>
      <aside className="sidebar">
        <div className="skeleton skeleton-block" style={{ height: 32, marginBottom: 12 }} />
        {[0, 1, 2].map((i) => (
          <div key={i} className="skeleton-schema-group">
            <div className="skeleton skeleton-block" style={{ height: 20, marginBottom: 8 }} />
            <div className="skeleton skeleton-block skeleton-indent" style={{ height: 14 }} />
            <div className="skeleton skeleton-block skeleton-indent" style={{ height: 14 }} />
          </div>
        ))}
      </aside>
      <section className="detail-panel">
        <div
          className="skeleton skeleton-block"
          style={{ height: 28, width: "40%", marginBottom: 20 }}
        />
        {[0, 1, 2, 3].map((i) => (
          <div
            key={i}
            className="skeleton skeleton-block"
            style={{ height: 18, marginBottom: 10 }}
          />
        ))}
      </section>
    </div>
  );
}
