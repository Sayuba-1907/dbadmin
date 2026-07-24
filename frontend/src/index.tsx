import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import reportWebVitals from "./reportWebVitals";
// i18n kurulumu App'ten once, en tepede import edilmeli — App ilk render oldugunda
// t() fonksiyonlarinin kullanacagi ceviri sozlukleri zaten yuklu olmali.
import "./i18n";

// Uygulamanin gercek giris noktasi: public/index.html'deki <div id="root"> icine
// React agacini burada mount ediyoruz. Baska bir yerde "ReactDOM.render" gormeyeceksin,
// bu tek satir tum uygulamayi baslatiyor.
const root = ReactDOM.createRoot(document.getElementById("root") as HTMLElement);
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
