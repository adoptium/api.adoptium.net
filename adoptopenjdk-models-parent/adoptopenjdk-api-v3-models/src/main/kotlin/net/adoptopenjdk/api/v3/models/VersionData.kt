package net.adoptopenjdk.api.v3.models

import net.adoptopenjdk.api.v3.dataSources.models.Releases
import org.eclipse.microprofile.openapi.annotations.media.Schema

class VersionData : Comparable<VersionData> {

    val major: Int
    val minor: Int
    val security: Int
    val patch: Int?
    val pre: String?
    val adopt_build_number: Int?

    @Schema(example = "11.0.0+28")
    val semver: String

    @Schema(example = "11.0.4+10-201907081820")
    val openjdk_version: String
    val build: Int
    val optional: String?

    constructor(
        major: Int,
        minor: Int,
        security: Int,
        pre: String?,
        adopt_build_number: Int?,
        build: Int,
        optional: String?,
        openjdk_version: String,
        semver: String? = null,
        patch: Int? = null
    ) {
        this.major = major
        this.minor = minor
        this.security = security
        this.patch = patch
        this.pre = if (pre?.isNotEmpty() == true) pre else null
        this.adopt_build_number = if (adopt_build_number != null && adopt_build_number != 0) adopt_build_number else null
        this.build = build
        this.optional = if (optional?.isNotEmpty() == true) optional else null
        this.openjdk_version = openjdk_version
        this.semver = semver ?: formSemver()
    }

    // i.e 11.0.1+11.1
    fun formSemver(): String {
        var semver = major.toString() + "." + minor + "." + security

        if (pre?.isNotEmpty() == true) {
            semver += "-$pre"
        }

        var metadata = listOf<String>()

        // 100 to match the same change made in the build repo
        val buildOffset = if (patch != null && patch != 0) patch * 100 else 0

        if (build != 0) {
            metadata = metadata.plus((buildOffset + build).toString())
        }

        if (adopt_build_number != null && adopt_build_number != 0) {
            metadata = metadata.plus(adopt_build_number.toString())
        }

        if (optional?.isNotEmpty() == true) {
            metadata = metadata.plus(optional)
        }

        if (metadata.isNotEmpty()) {
            semver += "+" + metadata.joinToString(".")
        }

        return semver
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + security
        result = 31 * result + (patch?.hashCode() ?: 0)
        result = 31 * result + (pre?.hashCode() ?: 0)
        result = 31 * result + (adopt_build_number ?: 0)
        result = 31 * result + openjdk_version.hashCode()
        result = 31 * result + build
        result = 31 * result + (optional?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VersionData

        if (openjdk_version != other.openjdk_version) return false

        return compareVersionNumber(other)
    }

    fun compareVersionNumber(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VersionData

        if (major != other.major) return false
        if (minor != other.minor) return false
        if (security != other.security) return false
        if (patch != other.patch) return false
        if (pre != other.pre) return false
        if (build != other.build) return false
        if (optional != other.optional) return false
        if (adopt_build_number != other.adopt_build_number) return false
        return true
    }

    override fun compareTo(other: VersionData): Int {
        return COMPARATOR.compare(this, other)
    }

    override fun toString(): String {
        return "VersionData(major=$major, minor=$minor, security=$security, patch=$patch, pre=$pre, adopt_build_number=$adopt_build_number, semver='$semver', openjdk_version='$openjdk_version', build=$build, optional=$optional)"
    }

    companion object {
        val COMPARATOR = compareBy<VersionData> { it.major }
            .thenBy { it.minor }
            .thenBy { it.security }
            .thenBy { it.patch }
            .then(Releases.PRE_SORTER)
            .thenBy { it.build }
            .thenBy { it.adopt_build_number }
            .thenBy { it.optional }
    }
}
