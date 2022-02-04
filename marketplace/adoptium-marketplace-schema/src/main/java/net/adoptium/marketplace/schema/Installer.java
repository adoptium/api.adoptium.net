package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Installer extends Asset {

    @JsonCreator
    public Installer(
            @JsonProperty("name") String name,
            @JsonProperty("link") String link,
            @JsonProperty("size") Long size,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("checksum_link") String checksum_link,
            @JsonProperty("signature_link") String signature_link,
            @JsonProperty("metadata_link") String metadata_link
    ) {
        super(name, link, size, checksum, checksum_link, signature_link, metadata_link);
    }
}
