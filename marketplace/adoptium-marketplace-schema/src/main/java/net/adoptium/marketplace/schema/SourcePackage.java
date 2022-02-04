package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class SourcePackage {

    @Schema(example = "OpenJDK8U-sources_8u232b09.tar.gz")
    private final String name;

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-upstream-binaries/releases/download/jdk8u232-b09/OpenJDK8U-sources_8u232b09.tar.gz")
    private final String link;

    @Schema(example = "82573385")
    private final Long size;

    @JsonCreator
    public SourcePackage(
            @JsonProperty("name") String name,
            @JsonProperty("link") String link,
            @JsonProperty("size") Long size) {
        this.name = name;
        this.link = link;
        this.size = size;
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
}
