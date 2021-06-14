package net.adoptopenjdk.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import java.util.function.Predicate

class Release {

    @Schema(example = "VXNlci0xMA==")
    val id: String

    @Schema(example = "https://github.com/AdoptOpenJDK/openjdk8-openj9-releases/ga/tag/jdk8u162-b12_openj9-0.8.0")
    val release_link: String

    @Schema(example = "jdk8u162-b12_openj9-0.8.0")
    val release_name: String

    val timestamp: DateTime

    val updated_at: DateTime

    @Schema(type = SchemaType.ARRAY, implementation = Binary::class)
    val binaries: Array<Binary>

    @Schema(example = "7128")
    val download_count: Long

    val release_type: ReleaseType

    val vendor: Vendor

    val version_data: VersionData

    val source: SourcePackage?

    @JsonCreator
    constructor(
        @JsonProperty("id") id: String,
        @JsonProperty("release_type") release_type: ReleaseType,
        @JsonProperty("release_link") release_link: String,
        @JsonProperty("release_name") release_name: String,
        @JsonProperty("timestamp") timestamp: DateTime,
        @JsonProperty("updated_at") updated_at: DateTime,
        @JsonProperty("binaries") binaries: Array<Binary>,
        @JsonProperty("download_count") download_count: Long,
        @JsonProperty("vendor") vendor: Vendor,
        @JsonProperty("version_data") version_data: VersionData,
        @JsonProperty("source") source: SourcePackage? = null
    ) {
        this.id = id
        this.release_type = release_type
        this.release_link = release_link
        this.release_name = release_name
        this.timestamp = timestamp
        this.updated_at = updated_at
        this.binaries = binaries
        this.download_count = download_count
        this.vendor = vendor
        this.version_data = version_data
        this.source = source
    }

    constructor(release: Release, binaries: Array<Binary>) {
        this.id = release.id
        this.release_type = release.release_type
        this.release_link = release.release_link
        this.release_name = release.release_name
        this.timestamp = release.timestamp
        this.updated_at = release.updated_at
        this.binaries = binaries
        this.download_count = release.download_count
        this.vendor = release.vendor
        this.version_data = release.version_data
        this.source = release.source
    }

    fun filterBinaries(binaryFilter: Predicate<Binary>): Release {
        return Release(this, binaries.filter { binaryFilter.test(it) }.toTypedArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Release

        if (id != other.id) return false
        if (release_link != other.release_link) return false
        if (release_name != other.release_name) return false
        if (timestamp != other.timestamp) return false
        if (updated_at != other.updated_at) return false
        if (!binaries.contentEquals(other.binaries)) return false
        if (release_type != other.release_type) return false
        if (vendor != other.vendor) return false
        if (version_data != other.version_data) return false
        if (source != other.source) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + release_link.hashCode()
        result = 31 * result + release_name.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + updated_at.hashCode()
        result = 31 * result + binaries.contentHashCode()
        result = 31 * result + release_type.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + version_data.hashCode()
        result = 31 * result + (source?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Release(id='$id', release_link='$release_link', release_name='$release_name', timestamp=$timestamp, updated_at=$updated_at, download_count=$download_count, release_type=$release_type, vendor=$vendor, version_data=$version_data, source=$source)"
    }
}
