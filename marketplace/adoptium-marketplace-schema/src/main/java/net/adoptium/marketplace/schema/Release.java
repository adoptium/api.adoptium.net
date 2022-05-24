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
    public static final String VERSION_DATA_NAME = "openjdk_version_data";
    public static final String VENDOR_PUBLIC_KEY_LINK_NAME = "vendor_public_key_link";
    public static final String LAST_UPDATED_TIMESTAMP_NAME = "last_updated_timestamp";

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
        name = LAST_UPDATED_TIMESTAMP_NAME,
        description = "Timestamp of the release creation",
        required = true
    )
    private final Date lastUpdatedTimestamp;

    @Schema(type = SchemaType.ARRAY, implementation = Binary.class, required = true)
    private final List<Binary> binaries;

    @Schema(required = true)
    private final Vendor vendor;

    @Schema(required = true, name = VERSION_DATA_NAME)
    private final OpenjdkVersionData openjdkVersionData;

    private final SourcePackage source;

    @Schema(
        required = false,
        name = VENDOR_PUBLIC_KEY_LINK_NAME,
        description = "Link to the public key which has been used to sign binaries within this release IF signature links are provided",
        example = "https://adoptium.net/publickey.asc"
    )
    private final String vendorPublicKeyLink;

    @JsonCreator
    public Release(
        @JsonProperty(RELEASE_LINK_NAME) String releaseLink,
        @JsonProperty(value = RELEASE_NAME_NAME, required = true) String releaseName,
        @JsonProperty(value = LAST_UPDATED_TIMESTAMP_NAME, required = true) Date lastUpdatedTimestamp,
        @JsonProperty("binaries") List<Binary> binaries,
        @JsonProperty(value = "vendor", required = true) Vendor vendor,
        @JsonProperty(value = VERSION_DATA_NAME, required = true) OpenjdkVersionData openjdkVersionData,
        @JsonProperty("source") SourcePackage source,
        @JsonProperty(VENDOR_PUBLIC_KEY_LINK_NAME) String vendorPublicKeyLink
    ) {
        this.releaseLink = releaseLink;
        this.releaseName = releaseName;
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
        this.binaries = binaries;
        this.vendor = vendor;
        this.openjdkVersionData = openjdkVersionData;
        this.source = source;
        this.vendorPublicKeyLink = vendorPublicKeyLink;
    }

    public Release(
        Release release,
        List<Binary> binaries
    ) {
        this(
            release.releaseLink,
            release.releaseName,
            release.lastUpdatedTimestamp,
            binaries,
            release.vendor,
            release.openjdkVersionData,
            release.source,
            release.vendorPublicKeyLink
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

    @JsonProperty(LAST_UPDATED_TIMESTAMP_NAME)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    public Date getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public List<Binary> getBinaries() {
        return binaries;
    }

    public Vendor getVendor() {
        return vendor;
    }

    @JsonProperty(VERSION_DATA_NAME)
    public OpenjdkVersionData getOpenjdkVersionData() {
        return openjdkVersionData;
    }

    public SourcePackage getSource() {
        return source;
    }

    @JsonProperty(VENDOR_PUBLIC_KEY_LINK_NAME)
    public String getVendorPublicKeyLink() {
        return vendorPublicKeyLink;
    }

    @Override
    public String toString() {
        return "Release{" +
            "releaseLink='" + releaseLink + '\'' +
            ", releaseName='" + releaseName + '\'' +
            ", lastUpdatedTimestamp=" + lastUpdatedTimestamp +
            ", vendor=" + vendor +
            ", openjdkVersionData=" + openjdkVersionData +
            ", source=" + source +
            ", vendorPublicKeyLink='" + vendorPublicKeyLink + '\'' +
            '}';
    }
}
