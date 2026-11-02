package net.adoptium.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData

@JsonIgnoreProperties(ignoreUnknown = true)
class ReleaseNote @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("title") val title: String?,
    @JsonProperty("priority") val priority: String?,
    @JsonProperty("component") val component: String?,
    @JsonProperty("subcomponent") val subcomponent: String?,
    @JsonProperty("link") val link: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("backportOf") val backportOf: String?,
    @JsonProperty("backportedBy") val backportedBy: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReleaseNote

        if (id != other.id) return false
        if (title != other.title) return false
        if (priority != other.priority) return false
        if (component != other.component) return false
        if (subcomponent != other.subcomponent) return false
        if (link != other.link) return false
        if (type != other.type) return false
        if (backportOf != other.backportOf) return false
        if (backportedBy != other.backportedBy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (priority?.hashCode() ?: 0)
        result = 31 * result + (component?.hashCode() ?: 0)
        result = 31 * result + (subcomponent?.hashCode() ?: 0)
        result = 31 * result + (link?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (backportOf?.hashCode() ?: 0)
        result = 31 * result + (backportedBy?.hashCode() ?: 0)
        return result
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class ReleaseNotes @JsonCreator constructor(
    @JsonProperty("version_data") val version_data: VersionData,
    @JsonProperty("vendor") val vendor: Vendor,
    @JsonProperty("id") val id: String,
    @JsonProperty("release_name") val release_name: String,
    @JsonProperty("release_notes") val release_notes: List<ReleaseNote>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReleaseNotes

        if (version_data != other.version_data) return false
        if (vendor != other.vendor) return false
        if (id != other.id) return false
        if (release_name != other.release_name) return false
        if (release_notes != other.release_notes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version_data.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + release_name.hashCode()
        result = 31 * result + release_notes.hashCode()
        return result
    }
}
