package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class Installer extends Asset {

    public static final String INSTALLER_TYPE_NAME = "installer_type";
    @Schema(example = "msi", description = "Type of the installer, i.e exe, msi, deb, dmg")
    private final String installerType;

    @JsonCreator
    public Installer(
        @JsonProperty("name") String name,
        @JsonProperty("link") String link,
        @JsonProperty(Asset.SHA256SUM_NAME) String sha256sum,
        @JsonProperty(Asset.SHA_256_SUM_LINK_NAME) String sha256sumLink,
        @JsonProperty(Asset.SIGNATURE_LINK_NAME) String signatureLink,
        @JsonProperty(INSTALLER_TYPE_NAME) String installerType
    ) {
        super(name, link, sha256sum, sha256sumLink, signatureLink);
        this.installerType = installerType;
    }

    @JsonProperty(INSTALLER_TYPE_NAME)
    public String getInstallerType() {
        return installerType;
    }

}
