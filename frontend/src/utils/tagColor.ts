const TAG_PALETTE_SIZE = 8;

/**
 * Tag isimleri kullanicinin girdigi serbest metin (sabit bir kume degil), bu yuzden isim/renk
 * eslemesi bir tablo yerine isimden turetilen bir hash ile yapiliyor - ayni isim her zaman
 * ayni renge duser, farkli isimlerin cogu farkli renk alir (8'li paletten).
 */
function hashTagName(name: string): number {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) | 0;
  }
  return Math.abs(hash) % TAG_PALETTE_SIZE;
}

export function tagColorStyle(name: string): { color: string; backgroundColor: string } {
  const index = hashTagName(name) + 1;
  return {
    color: `var(--color-tag-${index})`,
    backgroundColor: `var(--color-tag-${index}-bg)`,
  };
}
