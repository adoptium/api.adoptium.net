package net.adoptium.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Cdxa
import java.time.ZonedDateTime
import java.util.function.Predicate

class AdoptCdxaRepos {

    val repos: List<Cdxa>

    @JsonCreator
    constructor(
        @JsonProperty("repos")
        repos: List<Cdxa>
    ) {
        this.repos = repos
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
        return AdoptCdxaRepos(updated)
    }

    fun removeCdxa(att: Cdxa): AdoptCdxaRepos {
        val updated = repos.filter { it.id != att.id }
        return AdoptCdxaRepos(updated)
    }

    fun removeCdxais(cdxas: List<Cdxa>): AdoptCdxaRepos {
        if (cdxas.isEmpty()) {
            return this
        }
        return cdxas
            .fold(this) { repos, newAtt -> repos.removeCdxa(newAtt) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdoptCdxaRepos

        return repos == other.repos
    }

    override fun hashCode(): Int {
        return repos.hashCode()
    }
}
