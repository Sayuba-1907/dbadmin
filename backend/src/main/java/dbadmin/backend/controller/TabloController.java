package dbadmin.backend.controller;

import dbadmin.backend.dto.ChangeTabloSchemaRequest;
import dbadmin.backend.dto.ChangeTagRequest;
import dbadmin.backend.dto.CreateKolonRequest;
import dbadmin.backend.dto.CreateTabloRequest;
import dbadmin.backend.dto.KolonResponse;
import dbadmin.backend.dto.RenameRequest;
import dbadmin.backend.dto.TabloResponse;
import dbadmin.backend.service.KolonTanimi;
import dbadmin.backend.service.TabloService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tablo ve Kolon icin HTTP endpoint'leri. {@code @RestController} donen degeri otomatik
 * JSON'a cevirir; {@code @RequestMapping} tum metodlar icin ortak "/api/tablolar" on-ekini
 * belirler. Burasi ince bir katman: dogrulama/is mantigi yok, hepsi {@link TabloService}'te —
 * bu sinifin isi sadece HTTP <-> DTO <-> service cevirisi yapmak.
 */
@RestController
@RequestMapping("/api/tablolar")
public class TabloController {

    private final TabloService tabloService;

    public TabloController(TabloService tabloService) {
        this.tabloService = tabloService;
    }

    /** GET /api/tablolar — tum tablolarin listesi. Entity degil DTO ({@link TabloResponse}) doner; bkz. dto paketi neden ayri. */
    @GetMapping
    public List<TabloResponse> list() {
        return tabloService.listTablolar().stream()
                .map(TabloResponse::from)
                .toList();
    }

    /** GET /api/tablolar/{id} — tek bir tablonun detayi (kolonlariyla birlikte). */
    @GetMapping("/{id}")
    public TabloResponse get(@PathVariable Long id) {
        return TabloResponse.from(tabloService.getTablo(id));
    }

    /**
     * POST /api/tablolar — yeni tablo olusturur. {@code @ResponseStatus(CREATED)} basarili
     * sonucta 200 degil 201 dondurur (REST konvansiyonu: "yeni kaynak yaratildi").
     * Request'teki kolon listesi burada DTO'dan ({@link CreateKolonRequest}) service'in
     * bekledigi ic tipe ({@link KolonTanimi}) cevriliyor.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TabloResponse create(@RequestBody CreateTabloRequest request) {
        List<KolonTanimi> kolonTanimlari = request.kolonlar() == null
                ? List.of()
                : request.kolonlar().stream().map(CreateKolonRequest::toKolonTanimi).toList();
        return TabloResponse.from(tabloService.createTablo(request.name(), request.schemaId(), kolonTanimlari));
    }

    /** PATCH /api/tablolar/{id} — sadece ismi degistirir (PATCH = kismi guncelleme, PUT gibi tum kaynagi degistirmez). */
    @PatchMapping("/{id}")
    public TabloResponse rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return TabloResponse.from(tabloService.renameTablo(id, request.name()));
    }

    /** PATCH /api/tablolar/{id}/schema — tabloyu baska bir schema'ya tasir. */
    @PatchMapping("/{id}/schema")
    public TabloResponse changeSchema(@PathVariable Long id, @RequestBody ChangeTabloSchemaRequest request) {
        return TabloResponse.from(tabloService.changeSchema(id, request.schemaId()));
    }

    /** DELETE /api/tablolar/{id} — tablo ve kolonlarini siler. Govde donmedigi icin 204 No Content. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tabloService.deleteTablo(id);
    }

    /** POST /api/tablolar/{id}/kolonlar — var olan tabloya yeni kolon ekler. */
    @PostMapping("/{id}/kolonlar")
    @ResponseStatus(HttpStatus.CREATED)
    public KolonResponse addKolon(@PathVariable Long id, @RequestBody CreateKolonRequest request) {
        return KolonResponse.from(tabloService.addKolon(id, request.toKolonTanimi()));
    }

    /** DELETE /api/tablolar/{id}/kolonlar/{kolonId} — tek bir kolonu siler. */
    @DeleteMapping("/{id}/kolonlar/{kolonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKolon(@PathVariable Long id, @PathVariable Long kolonId) {
        tabloService.deleteKolon(id, kolonId);
    }

    /** PATCH .../name — kolonun adini degistirir (tipi degil, tip olusturulduktan sonra sabit). */
    @PatchMapping("/{id}/kolonlar/{kolonId}/name")
    public KolonResponse renameKolon(
            @PathVariable Long id, @PathVariable Long kolonId, @RequestBody RenameRequest request) {
        return KolonResponse.from(tabloService.renameKolon(id, kolonId, request.name()));
    }

    /** PATCH .../tag — kolonun etiketini degistirir/kaldirir (tagId null gonderilirse etiket kaldirilir). */
    @PatchMapping("/{id}/kolonlar/{kolonId}/tag")
    public KolonResponse changeKolonTag(
            @PathVariable Long id, @PathVariable Long kolonId, @RequestBody ChangeTagRequest request) {
        return KolonResponse.from(tabloService.changeKolonTag(id, kolonId, request.tagId()));
    }
}
