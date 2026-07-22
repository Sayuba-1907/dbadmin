package dbadmin.backend.service;

// Minimal input carrier for "define a column while creating a table".
// Not the API request/response shape - that's the DTO layer, which comes
// after the controller step. This just lets the service have a typed
// signature today instead of raw (String, String, Long) parameter lists.
//record : veri tasıyıcısıdır içi değişitirlemez çağrıldığında arka planda bazı metotları otomatik üretir. sadece veri tasımak
//için üretilmiştir.
public record KolonTanimi(String name, String type, Long tagId) {
}
