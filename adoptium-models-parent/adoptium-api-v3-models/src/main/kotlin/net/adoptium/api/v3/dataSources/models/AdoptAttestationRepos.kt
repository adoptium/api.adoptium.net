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
import net.adoptium.api.v3.models.Attestation
import java.time.ZonedDateTime
import java.util.function.Predicate

class AdoptAttestationRepos {

    val repos: List<Attestation>

    @JsonCreator
    constructor(
        @JsonProperty("repos")
        repos: List<Attestation>
    ) {
        this.repos = repos
    }

    fun getAttestations(): List<Attestation> {
        return repos
    }

    fun listAttestationsForAssetBinary(
            release_name: String?,
            vendor: Vendor?,
            os: OperatingSystem?,
            arch: Architecture?,
            image_type: ImageType?,
            jvm_impl: JvmImpl?): List<Attestation> {

        val result = repos.filter { it.release_name == release_name &&
                                    it.vendor == vendor &&
                                    it.os == os &&
                                    it.architecture == arch &&
                                    it.image_type == image_type &&
                                    it.jvm_impl == jvm_impl }

        return result
    }

    fun listAttestationsForTargetChecksum(
            target_checksum: String?): List<Attestation> {

        val result = repos.filter { it.target_checksum == target_checksum?.uppercase() }

        return result
    }

    fun listAttestationsForRelease(
            release_name: String?): List<Attestation> {

        val result = repos.filter { it.release_name == release_name }

        return result
    }

    fun addAll(attestations: List<Attestation>): AdoptAttestationRepos {
        if (attestations.isEmpty()) {
            return this
        }
        return attestations
            .fold(this) { repos, newAtt -> repos.addAttestation(newAtt) }
    }

    fun addAttestation(att: Attestation): AdoptAttestationRepos {
        val updated = repos + att
        return AdoptAttestationRepos(updated)
    }

    fun removeAttestation(att: Attestation): AdoptAttestationRepos {
        val updated = repos.filter { it.id != att.id }
        return AdoptAttestationRepos(updated)
    }

    fun removeAttestationis(attestations: List<Attestation>): AdoptAttestationRepos {
        if (attestations.isEmpty()) {
            return this
        }
        return attestations
            .fold(this) { repos, newAtt -> repos.removeAttestation(newAtt) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AdoptAttestationRepos

        return repos == other.repos
    }

    override fun hashCode(): Int {
        return repos.hashCode()
    }
}
