/**
 * DBAdmin'in amblemi: klasik "veritabani silindiri" ikonu, tek renk (accent yesil) cizgi.
 * Onceki duz yesil kare (.brand-mark) yerine hem login sayfasinda hem ust basliktaki marka
 * butonunda kullaniliyor — currentColor kullaniyor ki .brand-mark'in rengini CSS'ten
 * (App.css/login.css) miras alsin, boyut da font-size'a gore disaridan (className ile) verilsin.
 */
export function Logo({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <ellipse cx="12" cy="5" rx="8" ry="3" fill="currentColor" stroke="none" />
      <path d="M4 5v6c0 1.66 3.58 3 8 3s8-1.34 8-3V5" />
      <path d="M4 11v8c0 1.66 3.58 3 8 3s8-1.34 8-3v-8" />
    </svg>
  );
}
