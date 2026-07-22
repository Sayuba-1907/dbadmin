package dbadmin.backend.dto;

import java.util.List;
//CreateTabloRequest, dış dünyadan "Şu isimde bir
//tablo kur ve içine de şu kolonları yerleştir" emri geldiğinde kullanılan ana sipariş formudur.
public record CreateTabloRequest(String name, List<CreateKolonRequest> kolonlar) {
}
