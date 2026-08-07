import { createUser } from "./users";

/** Ayni gerekce (bkz. auth.test.ts): CreateUserRequest'in password alani frontend'de eslesmeli. */
test("createUser istegi username/password/role alanlariyla gonderir", async () => {
  let sentBody: unknown;
  global.fetch = jest.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
    sentBody = JSON.parse(init!.body as string);
    return {
      ok: true,
      status: 201,
      json: async () => ({ id: 1, username: "ayse", role: "VIEWER" }),
    };
  }) as jest.Mock;

  await createUser("ayse", "gizliParola1", "VIEWER");

  expect(sentBody).toEqual({ username: "ayse", password: "gizliParola1", role: "VIEWER" });
});
