package net.adoptium.marketplace.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class OpenjdkVersionData implements Comparable<OpenjdkVersionData> {

    private final Integer major;
    private final Optional<Integer> minor;
    private final Optional<Integer> security;
    private final Optional<Integer> patch;
    private final Optional<Integer> build;
    private final Optional<String> pre;
    private final Optional<String> optional;

    @Schema(example = "11.0.4+10-201907081820")
    private final String openjdk_version;


    /*
      "version_data": {
        "build": 12,
        "major": 17,
        "minor": 0,
        "openjdk_version": "17.0.1+12",
        "security": 1
      }
     */
    @JsonCreator
    public OpenjdkVersionData(
        @JsonProperty("major") Integer major,
        @JsonProperty("minor") Integer minor,
        @JsonProperty("security") Integer security,
        @JsonProperty("patch") Integer patch,
        @JsonProperty("pre") String pre,
        @JsonProperty("build") Integer build,
        @JsonProperty("optional") String optional,
        @JsonProperty("openjdk_version") String openjdk_version) {
        this.major = major;
        this.minor = Optional.ofNullable(minor);
        this.security = Optional.ofNullable(security);
        this.patch = Optional.ofNullable(patch);
        this.pre = Optional.ofNullable(pre);
        this.openjdk_version = openjdk_version;
        this.build = Optional.ofNullable(build);
        this.optional = Optional.ofNullable(optional);
    }

    public Integer getMajor() {
        return major;
    }

    public Optional<Integer> getMinor() {
        return minor;
    }

    public Optional<Integer> getSecurity() {
        return security;
    }

    public Optional<Integer> getPatch() {
        return patch;
    }

    public Optional<String> getPre() {
        return pre;
    }

    public Optional<Integer> getBuild() {
        return build;
    }

    public Optional<String> getOptional() {
        return optional;
    }

    public String getOpenjdk_version() {
        return openjdk_version;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenjdkVersionData that = (OpenjdkVersionData) o;
        return Objects.equals(major, that.major) && Objects.equals(minor, that.minor) && Objects.equals(security, that.security) && Objects.equals(patch, that.patch) && Objects.equals(build, that.build) && Objects.equals(pre, that.pre) && Objects.equals(optional, that.optional) && Objects.equals(openjdk_version, that.openjdk_version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, security, patch, build, pre, optional, openjdk_version);
    }

    @Override
    public int compareTo(OpenjdkVersionData openjdkVersionData) {
        if (!Objects.equals(this.major, openjdkVersionData.major)) {
            return Integer.compare(this.major, openjdkVersionData.major);
        } else if (compare(minor, openjdkVersionData.minor, Integer::compare) != 0) {
            return compare(minor, openjdkVersionData.minor, Integer::compare);
        } else if (compare(security, openjdkVersionData.security, Integer::compare) != 0) {
            return compare(security, openjdkVersionData.security, Integer::compare);
        } else if (compare(patch, openjdkVersionData.patch, Integer::compare) != 0) {
            return compare(patch, openjdkVersionData.patch, Integer::compare);
        } else if (compare(pre, openjdkVersionData.pre, OpenjdkVersionData::comparePre) != 0) {
            return compare(pre, openjdkVersionData.pre, OpenjdkVersionData::comparePre);
        } else if (compare(build, openjdkVersionData.build, Integer::compare) != 0) {
            return compare(build, openjdkVersionData.build, Integer::compare);
        } else if (compare(optional, openjdkVersionData.optional, String::compareTo) != 0) {
            return compare(optional, openjdkVersionData.optional, String::compareTo);
        }

        return 0;
    }

    private static int comparePre(String a, String b) {
        try {
            int aNum = Integer.parseInt(a);
            int bNum = Integer.parseInt(b);
            return Integer.compare(aNum, bNum);
        } catch (Exception e) {
            if (a == null && b == null) {
                return 0;
            } else if (a != null) {
                return a.compareTo(b);
            } else {
                return -1;
            }
        }
    }

    private <T> int compare(Optional<T> a, Optional<T> b, Comparator<T> comparator) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        } else if (a.isEmpty()) {
            return -1;
        } else if (b.isEmpty()) {
            return 1;
        } else {
            return comparator.compare(a.get(), b.get());
        }
    }

    @Schema(hidden = true)
    @JsonIgnore
    public boolean isLts() {
        return major == 8 || ((major - 11) % 6) == 0;
    }

    @Override
    public String toString() {
        return "OpenjdkVersionData{" +
            "major=" + major +
            ", minor=" + minor +
            ", security=" + security +
            ", patch=" + patch +
            ", build=" + build +
            ", pre=" + pre +
            ", optional=" + optional +
            ", openjdk_version='" + openjdk_version + '\'' +
            '}';
    }
}
