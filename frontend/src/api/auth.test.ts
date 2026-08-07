import { login } from "./auth";

/**
 * Bu tam olarak canli ortamda yasanan bir buga karsi koruma: bir refactor'de backend'in
 * LoginRequest alani parola'dan password'e cevrildi ama frontend'in gonderdigi govde
 * guncellenmemisti (hala {username, parola} gonderiyordu) — backend body'de "password"
 * alani bulamadigi icin gecerli bir sifreyle bile "AUTH_INVALID_CREDENTIALS" donuyordu.
 */
test("login istegi username/password alanlariyla gonderir (parola degil)", async () => {
  let sentBody: unknown;
  global.fetch = jest.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
    sentBody = JSON.parse(init!.body as string);
    return {
      ok: true,
      status: 200,
      json: async () => ({ token: "t", username: "admin", role: "ADMIN" }),
    };
  }) as jest.Mock;

  await login("admin", "admin123");

  expect(sentBody).toEqual({ username: "admin", password: "admin123" });
});
