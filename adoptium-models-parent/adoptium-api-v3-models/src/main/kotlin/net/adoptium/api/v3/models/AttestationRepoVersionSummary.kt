package net.adoptium.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import org.eclipse.microprofile.openapi.annotations.media.Schema

import java.time.Instant

// Model represents a summary of Attestation repository feature Version and containing releaseTags and their "committedDate"'s
class AttestationRepoVersionSummary {

    val vendor: Vendor

    val org: String

    val repo: String

    val featureVersion: Int

    val releaseTags: List<String>

    val committedDate: Instant?

    @JsonCreator
    constructor(
        vendor: Vendor,
        org: String,
        repo: String,
        featureVersion: Int,
        releaseTags: List<String>,
        committedDate: Instant?
    ) {
        this.vendor = vendor
        this.org = org
        this.repo = repo
        this.featureVersion = featureVersion
        this.releaseTags = releaseTags
        this.committedDate = committedDate
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttestationRepoVersionSummary

        if (featureVersion != other.featureVersion) return false
        if (releaseTags != other.releaseTags) return false
        if (vendor != other.vendor) return false
        if (org != other.org) return false
        if (repo != other.repo) return false
        if (committedDate != other.committedDate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = featureVersion.hashCode()
        result = 31 * result + releaseTags.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + org.hashCode()
        result = 31 * result + repo.hashCode()
        result = 31 * result + committedDate.hashCode()
        return result
    }

    override fun toString(): String {
        return "AttestationRepoVersionSummary(vendor='$vendor', org='$org', repo='$repo', featureVersion='$featureVersion', committedDate='$committedDate', releaseTags='$releaseTags')"
    }
}
