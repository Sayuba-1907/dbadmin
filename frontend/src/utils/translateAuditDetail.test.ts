import { translateAuditDetail } from "./translateAuditDetail";

test("tr dilinde dokunmadan doner", () => {
  expect(translateAuditDetail("schema olusturuldu: okul", "tr")).toBe("schema olusturuldu: okul");
});

test("null/bos detail bos string doner", () => {
  expect(translateAuditDetail(null, "en")).toBe("");
});

test("basit olusturma/silme kaliplarini cevirir", () => {
  expect(translateAuditDetail("schema olusturuldu: okul", "en")).toBe("schema created: okul");
  expect(translateAuditDetail("tag silindi: onemli", "en")).toBe("tag deleted: onemli");
  expect(translateAuditDetail("kullanici silindi: ahmet", "en")).toBe("user deleted: ahmet");
});

test("suffix'li kaliplari (parantezli) bare halinden once dogru esler", () => {
  expect(translateAuditDetail("tablo olusturuldu: ogrenci (schema=okul)", "en")).toBe(
    "table created: ogrenci (schema=okul)"
  );
  expect(translateAuditDetail("kolon eklendi: ad (tablo=5)", "en")).toBe(
    "column added: ad (table=5)"
  );
  // TABLE_UPDATED icindeki "bare" hali (tablo= suffix'i yok) — suffix'li kalip yanlislikla yutmamali.
  expect(translateAuditDetail("kolon eklendi: telefon", "en")).toBe("column added: telefon");
});

test("rename/degisti kaliplarini cevirir", () => {
  expect(translateAuditDetail("isim degisti: eski -> yeni", "en")).toBe("renamed: eski -> yeni");
  expect(translateAuditDetail("kolon adi degisti: eski -> yeni", "en")).toBe(
    "column renamed: eski -> yeni"
  );
  expect(translateAuditDetail("rol degisti: VIEWER -> ADMIN (kullanici=ahmet)", "en")).toBe(
    "role changed: VIEWER -> ADMIN (user=ahmet)"
  );
});

test("TABLE_UPDATED gibi '; ' ile birlesik coklu cumleyi parca parca cevirir", () => {
  const detail = "isim: eski -> yeni; kolon silindi: adres; kolon eklendi: telefon";
  expect(translateAuditDetail(detail, "en")).toBe(
    "name: eski -> yeni; column deleted: adres; column added: telefon"
  );
});

test("bilinmeyen bir kalip oldugu gibi kalir (kirilgan ama gurultusuz)", () => {
  expect(translateAuditDetail("hic bilinmeyen bir mesaj", "en")).toBe("hic bilinmeyen bir mesaj");
});
