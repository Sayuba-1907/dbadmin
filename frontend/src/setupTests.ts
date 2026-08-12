// jsdom (jest'in kullandigi DOM implementasyonu) ResizeObserver'i hic implement etmiyor —
// react-window'un List'i (bkz. TableSidebar.tsx) kendi boyutunu olcmek icin buna ihtiyac
// duyuyor, o yuzden testlerde "ResizeObserver is not defined" hatasi atiyordu. Gercek bir
// olcum yapmasi gerekmiyor (jsdom'da layout zaten yok) — sadece cagrilabilir olmasi yeterli.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

if (typeof globalThis.ResizeObserver === "undefined") {
  globalThis.ResizeObserver = ResizeObserverStub as unknown as typeof ResizeObserver;
}

export {};
