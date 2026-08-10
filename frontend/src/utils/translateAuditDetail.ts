/**
 * Audit log'un `detail` alanı, i18next'in çeviremeyeceği bir yer: backend (TableService,
 * SchemaService, TagService, UserService içindeki `auditLogService.record(...)` çağrıları) bu
 * metni hazır Türkçe bir cümle olarak üretip veritabanına öyle yazıyor — bir çeviri anahtarı
 * (`t("...")`) değil, serbest metin. EN arayüzde bile bu yüzden Türkçe kalıyordu.
 * <p>
 * Kalıcı çözüm backend'in kod+parametre üretmesi (ErrorResponse'daki `code`/`details` deseni
 * gibi) olurdu ama bu, dört servisteki ~19 çağrı noktasını değiştirmek demek — burada bilerek
 * daha küçük bir yama tercih edildi: backend'in ürettiği SABİT kalıpları (aşağıdaki liste)
 * tanıyıp EN'e çeviren bir eşleme. Yeni bir kalıp backend'e eklenirse (yeni bir OperationType
 * ya da mevcut birinin metni değişirse) burada da güncellenmesi gerekir — kırılgan ama pratik.
 * <p>
 * `TABLE_UPDATED` özel: birden fazla alt-cümle `"; "` ile birleştirilmiş tek bir satır olabilir
 * (bkz. TableService#applyChanges) — bu yüzden önce `"; "`'den bölünüp her parça ayrı ayrı
 * eşleştiriliyor, sonra tekrar `"; "` ile birleştiriliyor.
 */

interface DetailPattern {
  regex: RegExp;
  replace: (match: RegExpMatchArray) => string;
}

// Sira onemli: suffix'li (parantezli) kaliplar, ayni fiilin "bare" (parantezsiz) halinden
// ONCE denenmeli — aksi halde bare kalibin acgozlu (.+) grubu suffix'i de yutar.
const PATTERNS: DetailPattern[] = [
  {
    regex: /^tablo olusturuldu: (.+) \(schema=(.+)\)$/,
    replace: (m) => `table created: ${m[1]} (schema=${m[2]})`,
  },
  {
    regex: /^kolon eklendi: (.+) \(tablo=(.+)\)$/,
    replace: (m) => `column added: ${m[1]} (table=${m[2]})`,
  },
  {
    regex: /^kolon silindi: (.+) \(tablo=(.+)\)$/,
    replace: (m) => `column deleted: ${m[1]} (table=${m[2]})`,
  },
  {
    regex: /^kullanici olusturuldu: (.+) \(rol=(.+)\)$/,
    replace: (m) => `user created: ${m[1]} (role=${m[2]})`,
  },
  {
    regex: /^rol degisti: (.+) -> (.+) \(kullanici=(.+)\)$/,
    replace: (m) => `role changed: ${m[1]} -> ${m[2]} (user=${m[3]})`,
  },
  {
    regex: /^kolon adi degisti: (.+) -> (.+)$/,
    replace: (m) => `column renamed: ${m[1]} -> ${m[2]}`,
  },
  { regex: /^schema degisti: (.+) -> (.+)$/, replace: (m) => `schema changed: ${m[1]} -> ${m[2]}` },
  { regex: /^isim degisti: (.+) -> (.+)$/, replace: (m) => `renamed: ${m[1]} -> ${m[2]}` },
  { regex: /^isim: (.+) -> (.+)$/, replace: (m) => `name: ${m[1]} -> ${m[2]}` },
  {
    regex: /^tag degisti: kolon=(.+) tagId=(.+)$/,
    replace: (m) => `tag changed: column=${m[1]} tagId=${m[2]}`,
  },
  { regex: /^kolon guncellendi: id=(.+)$/, replace: (m) => `column updated: id=${m[1]}` },
  { regex: /^primary key eklendi: (.+)$/, replace: (m) => `primary key added: ${m[1]}` },
  { regex: /^primary key kaldirildi: (.+)$/, replace: (m) => `primary key removed: ${m[1]}` },
  { regex: /^schema olusturuldu: (.+)$/, replace: (m) => `schema created: ${m[1]}` },
  { regex: /^schema silindi: (.+)$/, replace: (m) => `schema deleted: ${m[1]}` },
  { regex: /^tablo silindi: (.+)$/, replace: (m) => `table deleted: ${m[1]}` },
  { regex: /^tag olusturuldu: (.+)$/, replace: (m) => `tag created: ${m[1]}` },
  { regex: /^tag silindi: (.+)$/, replace: (m) => `tag deleted: ${m[1]}` },
  { regex: /^kullanici silindi: (.+)$/, replace: (m) => `user deleted: ${m[1]}` },
  // TABLE_UPDATED'in "bare" (tablo/tagId suffix'i olmayan) alt-cumleleri — suffix'li halleri
  // yukarida denendigi icin sirada en sonda olmalarinin bir zarari yok.
  { regex: /^kolon eklendi: (.+)$/, replace: (m) => `column added: ${m[1]}` },
  { regex: /^kolon silindi: (.+)$/, replace: (m) => `column deleted: ${m[1]}` },
];

function translateClause(clause: string): string {
  for (const { regex, replace } of PATTERNS) {
    const match = clause.match(regex);
    if (match) {
      return replace(match);
    }
  }
  return clause;
}

/** `language !== "en"` ise dokunmadan doner — Türkçe arayüzde zaten olduğu gibi doğru. */
export function translateAuditDetail(detail: string | null, language: string): string {
  if (!detail || language !== "en") {
    return detail ?? "";
  }
  return detail.split("; ").map(translateClause).join("; ");
}
