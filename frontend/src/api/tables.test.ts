import { addColumn } from "./tables";

/**
 * Odak: addColumn ve getSchemaTables (schemas.test.ts) hicbir yerden dogrudan cagrilmiyor
 * (UI'da "kolon ekle" hep draft+applyChanges akisindan gecer) — bu yuzden bir onceki
 * refactor'de bu fonksiyonlarin URL'i bozulmus (template literal'daki ${tableId}
 * yanlislikla silinmis, "/api/tables//columns" gibi cift slash'li gecersiz bir yol kalmis)
 * ve hicbir test bunu yakalamamisti. Bu test SADECE dogru URL'in olusturuldugunu dogrular.
 */
test("addColumn dogru tableId'yi URL'e yerlestirir", async () => {
  global.fetch = jest.fn(async () => ({
    ok: true,
    status: 201,
    json: async () => ({
      id: 1,
      name: "yeni",
      type: "text",
      tagId: null,
      tagName: null,
      primaryKey: false,
    }),
  })) as jest.Mock;

  await addColumn(42, { name: "yeni", type: "text" });

  expect(global.fetch).toHaveBeenCalledWith(
    expect.stringMatching(/\/api\/tables\/42\/columns$/),
    expect.anything()
  );
});
