package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class Asset {

    @Schema(example = "OpenJDK8U-jre_x86-32_windows_hotspot_8u212b04.msi")
    private final String name;

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-binaries/ga/download/jdk8u212-b04/OpenJDK8U-jre_x86-32_windows_hotspot_8u212b04.msi")
    private final String link;

    @Schema(example = "82573385")
    private final Long size;

    @Schema(example = "dd28d6d2cde2b931caf94ac2422a2ad082ea62f0beee3bf7057317c53093de93")
    private final String checksum;

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/download/jdk8u162-b12_openj9-0.8.0/OpenJDK8-OPENJ9_x64_Linux_jdk8u162-b12_openj9-0.8.0.tar.gz.sha256.txt")
    private final String checksum_link;

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk11-upstream-binaries/releases/download/jdk-11.0.5%2B10/OpenJDK11U-jdk_x64_linux_11.0.5_10.tar.gz.sign")
    private final String signature_link;

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/download/jdk8u162-b12_openj9-0.8.0/OpenJDK8-OPENJ9_x64_Linux_jdk8u162-b12_openj9-0.8.0.tar.gz.json")
    private final String metadata_link;

    @JsonCreator
    public Asset(
            @JsonProperty("name") String name,
            @JsonProperty("link") String link,
            @JsonProperty("size") Long size,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("checksum_link") String checksum_link,
            @JsonProperty("signature_link") String signature_link,
            @JsonProperty("metadata_link") String metadata_link) {
        this.name = name;
        this.link = link;
        this.size = size;
        this.checksum = checksum;
        this.checksum_link = checksum_link;
        this.signature_link = signature_link;
        this.metadata_link = metadata_link;
    }

    public String getMetadata_link() {
        return metadata_link;
    }

    public String getName() {
        return name;
    }

    public String getLink() {
        return link;
    }

    public Long getSize() {
        return size;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getChecksum_link() {
        return checksum_link;
    }

    public String getSignature_link() {
        return signature_link;
    }
}
