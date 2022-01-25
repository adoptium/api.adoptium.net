package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public class VersionData {

    private final Integer major;
    private final Integer minor;
    private final Integer security;
    private final Integer patch;
    private final String pre;

    @Schema(example = "11.0.4+10-201907081820")
    private final String openjdk_version;

    private final Integer build;
    private final String optional;

    @JsonCreator
    public VersionData(
            @JsonProperty("major") Integer major,
            @JsonProperty("minor") Integer minor,
            @JsonProperty("security") Integer security,
            @JsonProperty("patch") Integer patch,
            @JsonProperty("pre") String pre,
            @JsonProperty("openjdk_version") String openjdk_version,
            @JsonProperty("build") Integer build,
            @JsonProperty("optional") String optional) {
        this.major = major;
        this.minor = minor;
        this.security = security;
        this.patch = patch;
        this.pre = pre;
        this.openjdk_version = openjdk_version;
        this.build = build;
        this.optional = optional;
    }

    public Integer getMajor() {
        return major;
    }

    public Integer getMinor() {
        return minor;
    }

    public Integer getSecurity() {
        return security;
    }

    public Integer getPatch() {
        return patch;
    }

    public String getPre() {
        return pre;
    }

    public String getOpenjdk_version() {
        return openjdk_version;
    }

    public Integer getBuild() {
        return build;
    }

    public String getOptional() {
        return optional;
    }
}
