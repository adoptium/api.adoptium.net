package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Installer extends Asset {

    @JsonCreator
    public Installer(
        @JsonProperty("name") String name,
        @JsonProperty("link") String link,
        @JsonProperty(Asset.SHA256SUM_NAME) String sha256sum,
        @JsonProperty(Asset.SHA_256_SUM_LINK_NAME) String sha256sumLink,
        @JsonProperty(Asset.SIGNATURE_LINK_NAME) String signatureLink
    ) {
        super(name, link, sha256sum, sha256sumLink, signatureLink);
    }
}
