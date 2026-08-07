package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * PATCH /api/tables/{id}/changes icin request govdesi — bir tablonun uzerinde
 * biriktirilmis butun degisiklikleri (isim, schema, silinen/eklenen/guncellenen kolonlar) TEK
 * istekte tasir. Servis katmani ({@code TableService.applyChanges}) hepsini tek bir
 * {@code @Transactional} icinde uygular: biri patlarsa hicbiri uygulanmaz — frontend'deki
 * "degisiklikleri biriktir, Kaydet'e basinca hepsi birden gitsin" akisinin karsiligi budur.
 * <p>
 * {@code newName}/{@code newSchemaId} sparse'dir: alan degismediyse null gonderilir, o alana
 * hic dokunulmaz. Bu, {@link ColumnUpdateRequest}'in "listede olan her satir tam nihai
 * durumunu tasir" kuralindan farklidir — cunku burada hangi ust seviye alanin degistigini
 * null/non-null ile ayirt edebiliyoruz (DataTable'in tek bir ismi/schema'si var, kolon listesi gibi
 * "bu satir listede mi degil mi" belirsizligi yok).
 */
public record TableUpdateRequest(
        @Schema(description = "Tablonun yeni adi. Degismediyse null/bos gecilir.",
                        example = "ogrenciler_v2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String newName,
        @Schema(description = "Tablonun tasinacagi hedef schema'nin id'si. Degismediyse null/bos gecilir.",
                        example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Long newSchemaId,
        @Schema(description = "Silinecek kolonlarin id listesi. Bos liste ya da null: hicbir kolon silinmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<Long> columnIdsToDelete,
        @Schema(description = "Eklenecek yeni kolonlarin listesi (mevcut CreateColumnRequest ile ayni sekil). "
                        + "Bos liste ya da null: hicbir kolon eklenmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<CreateColumnRequest> columnsToAdd,
        @Schema(description = "Var olan kolonlardan isim/etiket/PK isareti degisenlerin nihai halleri. "
                        + "Bos liste ya da null: hicbir var olan kolon guncellenmez.",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                List<ColumnUpdateRequest> columnsToUpdate) {

    /** Null gelen listeleri bos listeye cevirir — servis katmani null kontrolu yapmadan direkt donguye girebilsin diye. */
    public List<Long> columnIdsToDeleteOrEmpty() {
        return columnIdsToDelete == null ? List.of() : columnIdsToDelete;
    }

    public List<CreateColumnRequest> columnsToAddOrEmpty() {
        return columnsToAdd == null ? List.of() : columnsToAdd;
    }

    public List<ColumnUpdateRequest> columnsToUpdateOrEmpty() {
        return columnsToUpdate == null ? List.of() : columnsToUpdate;
    }
}
