package dbadmin.backend.controller;

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
//@RestController: Java kodlarını internetin evrensel veri taşıma formatı olan JSON'a çeviren otomatik bir tercüman ve dağıtım merkezidir.
@RestController
//RequestMapping, o sınıfın içindeki tüm metotlar için ortak bir "başlangıç rotası"
//(ortak adres) belirler. Kod tekrarını önler ve projeni son derece düzenli tutar!
@RequestMapping("/api/tablolar")
public class TabloController {

    private final TabloService tabloService;

    public TabloController(TabloService tabloService) {
        this.tabloService = tabloService;
    }

    @GetMapping
    public List<TabloResponse> list() {
        return tabloService.listTablolar().stream()
                .map(TabloResponse::from)
                .toList();
    }
//@GetMapping, veritabanından veri okumak ve dışarıya listelemek için kullanılan,
//veritabanında hiçbir şeyi silmeyen veya değiştirmeyen (salt okunur) en güvenli istek türüdür.
    @GetMapping("/{id}")
    public TabloResponse get(@PathVariable Long id) {
        return TabloResponse.from(tabloService.getTablo(id));
    }
    //@PostMapping, sisteme sıfırdan yeni bir şeyler eklemek (kayıt oluşturmak) için
    //kullanılır ve genelde işlemin başarılı olduğunu belirten 201 Created koduyla birlikte çalışır.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TabloResponse create(@RequestBody CreateTabloRequest request) {
        List<KolonTanimi> kolonTanimlari = request.kolonlar() == null
                ? List.of()
                : request.kolonlar().stream().map(CreateKolonRequest::toKolonTanimi).toList();
        return TabloResponse.from(tabloService.createTablo(request.name(), kolonTanimlari));
    }
        //@PatchMapping, kelime anlamı olarak "yama yapmak" veya "kısmi güncelleme yapmak" demektir.
        // Bütün evi yıkıp baştan yapmaz; sadece bozulan musluğu tamir eder.
    @PatchMapping("/{id}")
    public TabloResponse rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return TabloResponse.from(tabloService.renameTablo(id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tabloService.deleteTablo(id);
    }

    @PostMapping("/{id}/kolonlar")
    @ResponseStatus(HttpStatus.CREATED)
    public KolonResponse addKolon(@PathVariable Long id, @RequestBody CreateKolonRequest request) {
        return KolonResponse.from(tabloService.addKolon(id, request.toKolonTanimi()));
    }

    @DeleteMapping("/{id}/kolonlar/{kolonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKolon(@PathVariable Long id, @PathVariable Long kolonId) {
        tabloService.deleteKolon(id, kolonId);
    }

    @PatchMapping("/{id}/kolonlar/{kolonId}/name")
    public KolonResponse renameKolon(
            @PathVariable Long id, @PathVariable Long kolonId, @RequestBody RenameRequest request) {
        return KolonResponse.from(tabloService.renameKolon(id, kolonId, request.name()));
    }

    @PatchMapping("/{id}/kolonlar/{kolonId}/tag")
    public KolonResponse changeKolonTag(
            @PathVariable Long id, @PathVariable Long kolonId, @RequestBody ChangeTagRequest request) {
        return KolonResponse.from(tabloService.changeKolonTag(id, kolonId, request.tagId()));
    }
}
