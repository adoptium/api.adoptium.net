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

    private final Date updated_at;

    @Schema(type = SchemaType.ARRAY, implementation = Binary.class)
    private final List<Binary> binaries;

    private final Vendor vendor;

    private final VersionData version_data;

    private final SourcePackage source;

    @Schema(required = true, example = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17%2B35/OpenJDK17-jdk_x64_linux_hotspot_17_35.tar.gz.aqavit.zip")
    private final String aqavit_results_link;
    
    @Schema(example = "https://adoptium.net/tck_affidavit.html")
    private final String tck_affidavit_link;

    @JsonCreator
    public Release(
            @JsonProperty("release_link") String release_link,
            @JsonProperty("release_name") String release_name,
            @JsonProperty("timestamp") Date timestamp,
            @JsonProperty("updated_at") Date updated_at,
            @JsonProperty("binaries") List<Binary> binaries,
            @JsonProperty("vendor") Vendor vendor,
            @JsonProperty("version_data") VersionData version_data,
            @JsonProperty("source") SourcePackage source,
            @JsonProperty("aqavit_results_link") String aqavit_results_link,
            @JsonProperty("tck_affidavit_link") String tck_affidavit_link
    ) {
        this.release_link = release_link;
        this.release_name = release_name;
        this.timestamp = timestamp;
        this.updated_at = updated_at;
        this.binaries = binaries;
        this.vendor = vendor;
        this.version_data = version_data;
        this.source = source;
        this.aqavit_results_link = aqavit_results_link;
        this.tck_affidavit_link = tck_affidavit_link;
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

    public Date getUpdated_at() {
        return updated_at;
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

    public String getAqavit_results_link() {
        return aqavit_results_link;
    }

    public String getTck_affidavit_link() {
        return tck_affidavit_link;
    }
}
