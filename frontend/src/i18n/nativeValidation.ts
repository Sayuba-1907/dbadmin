import { FormEvent } from "react";
import { TFunction } from "i18next";

/**
 * Tarayicinin native `required` balonu, sayfanin secili diline degil tarayicinin kendi
 * arayuz diline gore metin gosterir (Chrome'un bilinen bir tuhafligi — `lang` attribute'unu
 * dikkate almaz). `onInvalid`'de `setCustomValidity` ile bu metni kendi i18next
 * sozlugumuzden veriyoruz; boylece balon hala native davranir (odaklanma, form submit'i
 * engelleme) ama gosterdigi yazi uygulamanin diliyle birlikte degisir.
 */
export function onRequiredInvalid(t: TFunction) {
  return (e: FormEvent<HTMLInputElement>) => {
    e.currentTarget.setCustomValidity(t("validation.required"));
  };
}

/**
 * `setCustomValidity` bir kere set edilince kullanici tekrar yazsa bile alan "invalid"
 * kalmaya devam eder — her onChange'de bunu temizlemezsek, dogru bir deger girilse dahi
 * form submit edilemez. onChange handler'larinin basina eklenir.
 */
export function clearCustomValidity(e: FormEvent<HTMLInputElement>) {
  e.currentTarget.setCustomValidity("");
}
