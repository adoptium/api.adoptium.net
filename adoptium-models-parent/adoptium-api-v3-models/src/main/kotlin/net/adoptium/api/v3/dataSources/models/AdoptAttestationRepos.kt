package net.adoptium.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import java.time.ZonedDateTime
import java.util.function.Predicate

class AdoptAttestationRepos {

    val repos: List<Attestation>

    @JsonCreator
    constructor(
        @JsonProperty("repos")
        @JsonDeserialize(keyAs = Int::class)
        repos: List<Attestation>
    ) {
        this.repos = repos
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
