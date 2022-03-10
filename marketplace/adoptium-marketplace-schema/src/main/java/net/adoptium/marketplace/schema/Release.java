package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Date;
import java.util.List;

public class Release {

    public static final String RELEASE_LINK_NAME = "release_link";
    public static final String RELEASE_NAME_NAME = "release_name";
    public static final String VERSION_DATA_NAME = "version_data";
    @Schema(
        example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/tag/jdk8u162-b12_openj9-0.8.0",
        name = RELEASE_LINK_NAME
    )
    private final String releaseLink;

    @Schema(
        example = "jdk8u162-b12_openj9-0.8.0",
        required = true,
        name = RELEASE_NAME_NAME
    )
    private final String releaseName;

    @Schema(
        description = "Timestamp of the release creation",
        required = true
    )
    private final Date timestamp;

    @Schema(type = SchemaType.ARRAY, implementation = Binary.class, required = true)
    private final List<Binary> binaries;

    @Schema(required = true)
    private final Vendor vendor;

    @Schema(required = true, name = VERSION_DATA_NAME)
    private final VersionData versionData;

    private final SourcePackage source;

    @JsonCreator
    public Release(
        @JsonProperty(RELEASE_LINK_NAME) String releaseLink,
        @JsonProperty(RELEASE_NAME_NAME) String releaseName,
        @JsonProperty("timestamp") Date timestamp,
        @JsonProperty("binaries") List<Binary> binaries,
        @JsonProperty("vendor") Vendor vendor,
        @JsonProperty(VERSION_DATA_NAME) VersionData versionData,
        @JsonProperty("source") SourcePackage source
    ) {
        this.releaseLink = releaseLink;
        this.releaseName = releaseName;
        this.timestamp = timestamp;
        this.binaries = binaries;
        this.vendor = vendor;
        this.versionData = versionData;
        this.source = source;
    }

    public Release(
        Release release,
        List<Binary> binaries
    ) {
        this(
            release.releaseLink,
            release.releaseName,
            release.timestamp,
            binaries,
            release.vendor,
            release.versionData,
            release.source
        );
    }

    @JsonProperty(RELEASE_LINK_NAME)
    public String getReleaseLink() {
        return releaseLink;
    }

    @JsonProperty(RELEASE_NAME_NAME)
    public String getReleaseName() {
        return releaseName;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    public Date getTimestamp() {
        return timestamp;
    }

    public List<Binary> getBinaries() {
        return binaries;
    }

    public Vendor getVendor() {
        return vendor;
    }

    @JsonProperty(VERSION_DATA_NAME)
    public VersionData getVersionData() {
        return versionData;
    }

    public SourcePackage getSource() {
        return source;
    }
}
