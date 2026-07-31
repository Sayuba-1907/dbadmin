package dbadmin.backend.dto; // Dosyayı dto klasörüne taşıdığımız için paket adı değişti

import dbadmin.backend.entity.Tablo; // Entity'nin bulunduğu doğru yolu buraya yazdık

public record TabloSummaryResponse(
        Long id,
        String name,
        int kolonSayisi
) {
    public static TabloSummaryResponse from(Tablo tablo) {
        // Kolonlar null gelirse hata almamak için ufak bir kontrol yapıyoruz
        int kolonCount = (tablo.getKolonlar() != null) ? tablo.getKolonlar().size() : 0;
        return new TabloSummaryResponse(tablo.getId(), tablo.getName(), kolonCount);
    }
}