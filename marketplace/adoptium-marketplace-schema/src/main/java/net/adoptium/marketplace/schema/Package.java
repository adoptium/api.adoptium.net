package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Package extends Asset {

    @JsonCreator
    public Package(
            @JsonProperty("name") String name,
            @JsonProperty("link") String link,
            @JsonProperty("size") Long size,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("checksum_link") String checksum_link,
            @JsonProperty(value = "signature_link", required = false) String signature_link,
            @JsonProperty("metadata_link") String metadata_link) {
        super(name, link, size, checksum, checksum_link, signature_link, metadata_link);
    }
}
