package net.adoptium.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import java.time.Instant

class AdoptCdxaRepos {

    val repos: List<Cdxa>
    val lastModified: Instant?  // Set to the "latest" committedDate of cdxas, to enable incrementalUpdate to determine changes

    @JsonCreator
    constructor(
        @JsonProperty("repos")
        repos: List<Cdxa>,
        @JsonProperty("lastModified")
        lastModified: Instant? = null
    ) {
        this.repos = repos
        this.lastModified = lastModified
    }

    fun getCdxas(): List<Cdxa> {
        return repos
    }

    fun listCdxasForAssetBinary(
            release_name: String?,
            vendor: Vendor?,
            os: OperatingSystem?,
            arch: Architecture?,
            image_type: ImageType?,
            jvm_impl: JvmImpl?): List<Cdxa> {

        val result = repos.filter { it.release_name == release_name &&
                                    it.vendor == vendor &&
                                    it.os == os &&
                                    it.architecture == arch &&
                                    it.image_type == image_type &&
                                    it.jvm_impl == jvm_impl }

        return result
    }

    fun listCdxasForTargetChecksum(
            target_checksum: String?): List<Cdxa> {

        val result = repos.filter { it.target_checksum == target_checksum?.uppercase() }

        return result
    }

    fun listCdxasForRelease(
            release_name: String?): List<Cdxa> {

        val result = repos.filter { it.release_name == release_name }

        return result
    }

    fun addAll(cdxas: List<Cdxa>): AdoptCdxaRepos {
        if (cdxas.isEmpty()) {
            return this
        }
        return cdxas
            .fold(this) { repos, newAtt -> repos.addCdxa(newAtt) }
    }

    fun addCdxa(att: Cdxa): AdoptCdxaRepos {
        val updated = repos + att
        return AdoptCdxaRepos(updated, lastModified)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdoptCdxaRepos

        if (repos != other.repos) return false
        if (lastModified != other.lastModified) return false

        return true
    }

    override fun hashCode(): Int {
        var result = repos.hashCode()
        result = 31 * result + lastModified.hashCode()
        return result
    }
}
