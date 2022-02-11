package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;

public class Release {

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/tag/jdk8u162-b12_openj9-0.8.0")
    private final String release_link;

    @Schema(example = "jdk8u162-b12_openj9-0.8.0")
    private final String release_name;

    private final Date timestamp;

    @Schema(type = SchemaType.ARRAY, implementation = Binary.class)
    private final List<Binary> binaries;

    private final Vendor vendor;

    private final VersionData version_data;

    private final SourcePackage source;

    @JsonCreator
    public Release(
        @JsonProperty("release_link") String release_link,
        @JsonProperty("release_name") String release_name,
        @JsonProperty("timestamp") Date timestamp,
        @JsonProperty("binaries") List<Binary> binaries,
        @JsonProperty("vendor") Vendor vendor,
        @JsonProperty("version_data") VersionData version_data,
        @JsonProperty("source") SourcePackage source
    ) {
        this.release_link = release_link;
        this.release_name = release_name;
        this.timestamp = timestamp;
        this.binaries = binaries;
        this.vendor = vendor;
        this.version_data = version_data;
        this.source = source;
    }


    public String getRelease_link() {
        return release_link;
    }

    public String getRelease_name() {
        return release_name;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public List<Binary> getBinaries() {
        return binaries;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public VersionData getVersion_data() {
        return version_data;
    }

    public SourcePackage getSource() {
        return source;
    }


}
