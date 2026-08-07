import { getSchemaTables } from "./schemas";

/** Ayni gerekce (bkz. tables.test.ts): kullanilmayan bir fonksiyonun URL'i sessizce bozulabilir. */
test("getSchemaTables dogru schemaId'yi URL'e yerlestirir", async () => {
  global.fetch = jest.fn(async () => ({
    ok: true,
    status: 200,
    json: async () => [],
  })) as jest.Mock;

  await getSchemaTables(7);

  expect(global.fetch).toHaveBeenCalledWith(
    expect.stringMatching(/\/api\/schemas\/7\/tables$/),
    expect.anything()
  );
});
