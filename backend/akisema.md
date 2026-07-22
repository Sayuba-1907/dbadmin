flowchart TD
%% Başlangıç
Start([Kullanıcı - React Arayüzü]) -->|Yeni Tablo Bilgilerini JSON ile Gönderir| Controller(Controller / API)

    %% Controller Adımı
    Controller -->|Gelen JSON'ı DTO'ya Çevirir| Service(Service Katmanı)
    
    %% Service Karar Mekanizması
    Service --> IsValid{Kurallar Uygun mu? <br> Validation & Conflict Check}
    
    %% Hata Senaryosu
    IsValid -->|Hayır Hata Var| ThrowException[Exception Fırlatılır]
    ThrowException --> ReturnError[Controller 400 veya 409 Hata Kodu Döner]
    ReturnError --> UI_Error([Ekranda Kırmızı Hata Bildirimi Gösterilir])
    
    %% Başarı Senaryosu
    IsValid -->|Evet Kurallar Geçildi| ToEntity[DTO, Entity Nesnesine Çevrilir]
    ToEntity --> Repo[Repository Katmanı]
    
    %% Transaction Bloğu
    subgraph Transaction_Semsiyesi [ @Transactional - İşlem Bütünlüğü ]
        Repo -->|Hibernate ile INSERT| MetaDB[(PostgreSQL - Metadata Kaydı)]
        MetaDB --> DDL[TableDdlExecutor Çağrılır]
        DDL -->|Gerçek CREATE TABLE Komutu| RealDB[(PostgreSQL - Fiziksel Tablo Oluşumu)]
    end
    
    RealDB --> IsSuccess{Veritabanı İşlemi <br> Başarılı mı?}
    
    %% Rollback
    IsSuccess -->|Hayır SQL Hatası Çıktı| Rollback[ROLLBACK - Tüm İşlemler Geri Alınır]
    Rollback --> ThrowException
    
    %% Commit ve Dönüş
    IsSuccess -->|Evet Her Şey Sorunsuz| Commit[COMMIT - Değişiklikler Kalıcı Olur]
    Commit --> ToDTO[Oluşan Entity Tekrar DTO'ya Çevrilir]
    ToDTO --> ReturnSuccess[Controller Başarı Yanıtı Döner - HTTP 201 Created]
    ReturnSuccess --> UI_Success([Ekranda Yeşil Başarı Bildirimi Gösterilir])