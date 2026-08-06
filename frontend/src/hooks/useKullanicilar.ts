import { useCallback, useState } from "react";
import { Rol } from "../api/auth";
import {
  Kullanici,
  changeKullaniciRol,
  createKullanici,
  deleteKullanici,
  getKullanicilar,
} from "../api/kullanicilar";

/**
 * Kullanici domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md Faz 2 Adim 2.3. useTags'le ayni kalip (otomatik mount-cekme
 * YOK — bu uc zaten sadece ADMIN "Kullanicilar" sekmesine girince cekiliyordu), ek olarak
 * {@code changeRol} mutasyonu var.
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4). {@code changeRol} basarisiz olursa
 * (ör. CONFLICT_LAST_ADMIN) Dashboard bilerek listeyi yeniden cekmiyor — bkz. oradaki aciklama.
 */
export function useKullanicilar() {
  const [kullanicilar, setKullanicilar] = useState<Kullanici[]>([]);
  const [yukleniyor, setYukleniyor] = useState(false);

  const yenile = useCallback(async () => {
    setYukleniyor(true);
    try {
      setKullanicilar(await getKullanicilar());
    } finally {
      setYukleniyor(false);
    }
  }, []);

  const create = useCallback(
    async (kullaniciAdi: string, parola: string, rol: Rol) => {
      const created = await createKullanici(kullaniciAdi, parola, rol);
      await yenile();
      return created;
    },
    [yenile]
  );

  const changeRol = useCallback(
    async (id: number, rol: Rol) => {
      const updated = await changeKullaniciRol(id, rol);
      await yenile();
      return updated;
    },
    [yenile]
  );

  const remove = useCallback(async (id: number) => {
    await deleteKullanici(id);
  }, []);

  return {
    kullanicilar,
    yukleniyor,
    yenile,
    createKullanici: create,
    changeKullaniciRol: changeRol,
    deleteKullanici: remove,
    // useSchemalar/useTags'teki ayni gerekce: Dashboard'daki kullanici silme "hemen kaldir, 5sn
    // icinde Geri Al'a basilmazsa asil API cagrisini yap" desenini kullanir.
    setKullanicilarOptimistic: setKullanicilar,
  };
}
