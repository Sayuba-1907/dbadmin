package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PATCH /api/tablolar/{id}/degisiklikler icin request govdesi — bir tablonun uzerinde
 * biriktirilmis butun degisiklikleri (isim, schema, silinen/eklenen/guncellenen kolonlar) TEK
 * istekte tasir. Servis katmani ({@code TabloService.applyChanges}) hepsini tek bir
 * {@code @Transactional} icinde uygular: biri patlarsa hicbiri uygulanmaz — frontend'deki
 * "degisiklikleri biriktir, Kaydet'e basinca hepsi birden gitsin" akisinin karsiligi budur.
 * <p>
 * {@code yeniIsim}/{@code yeniSchemaId} sparse'dir: alan degismediyse null gonderilir, o alana
 * hic dokunulmaz. Bu, {@link KolonGuncellemeRequest}'in "listede olan her satir tam nihai
 * durumunu tasir" kuralindan farklidir — cunku burada hangi ust seviye alanin degistigini
 * null/non-null ile ayirt edebiliyoruz (Tablo'nun tek bir ismi/schema'si var, kolon listesi gibi
 * "bu satir listede mi degil mi" belirsizligi yok).
 */
public record TabloUpdateRequest(
        @Schema(description = "Tablonun yeni adi. Degismediyse null/bos gecilir.",
                        example = "ogrenciler_v2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String yeniIsim,
        @Schema(description = "Tablonun tasinacagi hedef schema'nin id'si. Degismediyse null/bos gecilir.",
                        example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long yeniSchemaId,
        @Schema(description = "Silinecek kolonlarin id listesi. Bos liste ya da null: hicbir kolon silinmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<Long> silinecekKolonIdler,
        @Schema(description = "Eklenecek yeni kolonlarin listesi (mevcut CreateKolonRequest ile ayni sekil). "
                        + "Bos liste ya da null: hicbir kolon eklenmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<CreateKolonRequest> eklenecekKolonlar,
        @Schema(description = "Var olan kolonlardan isim/etiket/PK isareti degisenlerin nihai halleri. "
                        + "Bos liste ya da null: hicbir var olan kolon guncellenmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<KolonGuncellemeRequest> guncellenecekKolonlar) {

    /** Null gelen listeleri bos listeye cevirir — servis katmani null kontrolu yapmadan direkt donguye girebilsin diye. */
    public List<Long> silinecekKolonIdlerVeyaBos() {
        return silinecekKolonIdler == null ? List.of() : silinecekKolonIdler;
    }

    public List<CreateKolonRequest> eklenecekKolonlarVeyaBos() {
        return eklenecekKolonlar == null ? List.of() : eklenecekKolonlar;
    }

    public List<KolonGuncellemeRequest> guncellenecekKolonlarVeyaBos() {
        return guncellenecekKolonlar == null ? List.of() : guncellenecekKolonlar;
    }
}
