package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Package extends Asset {

    @JsonCreator
    public Package(
        @JsonProperty("name") String name,
        @JsonProperty("link") String link,
        @JsonProperty("checksum") String checksum,
        @JsonProperty(Asset.CHECKSUM_LINK_NAME) String checksumLink,
        @JsonProperty(Asset.SIGNATURE_LINK_NAME) String signatureLink,
        @JsonProperty(Asset.METADATA_LINK_NAME) String metadataLink
    ) {
        super(name, link, checksum, checksumLink, signatureLink, metadataLink);
    }
}
