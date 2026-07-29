package dbadmin.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Var olan bir kolonun birincil anahtar isaretini degistirme request'i.
 * {@code true} kolonu tablonun PRIMARY KEY'ine ekler, {@code false} cikarir — her iki durumda da
 * tablonun gercek {@code PRIMARY KEY} constraint'i guncel isaretli kolon setiyle yeniden kurulur.
 */
public record ChangePrimaryKeyRequest(
        @Schema(
                        description = "Kolon birincil anahtarin parcasi olsun mu. true ise tablonun "
                                + "PRIMARY KEY'ine eklenir (zaten isaretli kolonlar varsa composite PK olur), "
                                + "false ise cikarilir. Cikarma sonrasi hicbir isaretli kolon kalmazsa tablo "
                                + "primary key'siz kalir.",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Boolean primaryKey) {
}
