package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Package extends Asset {

    @JsonCreator
    public Package(
            @JsonProperty("name") String name,
            @JsonProperty("link") String link,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("checksum_link") String checksumLink,
            @JsonProperty("signature_link") String signatureLink,
            @JsonProperty("metadata_link") String metadataLink) {
        super(name, link, checksum, checksumLink, signatureLink, metadataLink);
    }
}
