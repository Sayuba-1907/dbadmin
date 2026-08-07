import { useCallback, useState } from "react";
import { Role } from "../api/auth";
import { User, changeUserRole, createUser, deleteUser, getUsers } from "../api/users";

/**
 * User domain'inin okuma+yazma sorumlulugunu Dashboard'dan ayiran custom hook — bkz.
 * requirement-react-custom-hooks.md Faz 2 Adim 2.3. useTags'le ayni kalip (otomatik mount-cekme
 * YOK — bu uc zaten sadece ADMIN "Kullanicilar" sekmesine girince cekiliyordu), ek olarak
 * {@code changeRole} mutasyonu var.
 * <p>
 * Hata yonetimi: bu hook hatayi YUTMAZ, firlatir (Req-2.4). {@code changeRole} basarisiz olursa
 * (ör. CONFLICT_LAST_ADMIN) Dashboard bilerek listeyi yeniden cekmiyor — bkz. oradaki aciklama.
 */
export function useUsers() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setUsers(await getUsers());
    } finally {
      setLoading(false);
    }
  }, []);

  const create = useCallback(
    async (username: string, password: string, role: Role) => {
      const created = await createUser(username, password, role);
      await refresh();
      return created;
    },
    [refresh]
  );

  const changeRole = useCallback(
    async (id: number, role: Role) => {
      const updated = await changeUserRole(id, role);
      await refresh();
      return updated;
    },
    [refresh]
  );

  const remove = useCallback(async (id: number) => {
    await deleteUser(id);
  }, []);

  return {
    users,
    loading,
    refresh,
    createUser: create,
    changeUserRole: changeRole,
    deleteUser: remove,
    // useSchemas/useTags'teki ayni gerekce: Dashboard'daki kullanici silme "hemen kaldir, 5sn
    // icinde Geri Al'a basilmazsa asil API cagrisini yap" desenini kullanir.
    setUsersOptimistic: setUsers,
  };
}
