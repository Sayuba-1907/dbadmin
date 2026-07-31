import { ReportHandler } from "web-vitals";

/**
 * Create-React-App'in standart performans olcum dosyasi (bu projeye ozel degil).
 * onPerfEntry verilmezse hicbir sey yapmaz; index.tsx'te parametresiz cagriliyor, yani
 * su an devre disi — sadece CRA'nin varsayilan iskeleti.
 */
const reportWebVitals = (onPerfEntry?: ReportHandler) => {
  if (onPerfEntry && onPerfEntry instanceof Function) {
    import("web-vitals").then(({ getCLS, getFID, getFCP, getLCP, getTTFB }) => {
      getCLS(onPerfEntry);
      getFID(onPerfEntry);
      getFCP(onPerfEntry);
      getLCP(onPerfEntry);
      getTTFB(onPerfEntry);
    });
  }
};

export default reportWebVitals;
