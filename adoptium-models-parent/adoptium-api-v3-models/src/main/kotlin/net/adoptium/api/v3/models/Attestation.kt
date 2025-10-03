package net.adoptium.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import org.eclipse.microprofile.openapi.annotations.media.Schema

import java.time.Instant

class Attestation {

    val id: String

    val filename: String

    val featureVersion: Int

    @Schema(example = "jdk-21.0.5+11")
    val release_name: String?

    val os: OperatingSystem

    val architecture: Architecture

    val image_type: ImageType

    val jvm_impl: JvmImpl

    val vendor: Vendor

    val committedDate: Instant?

    @Schema(description = "Assessor checksum of attested target")
    val target_checksum: String?

    @Schema(example = "Acme Ltd")
    val assessor_org: String?

    @Schema(example = "We claim a verified reproducible build.")
    val assessor_affirmation: String?

    @Schema(example = "VERIFIED_REPRODUCIBLE_BUILD")
    val assessor_claim_predicate: String?

    @Schema(example = "https://github.com/adoptium/temurin-attestations/blob/main/21/jdk_21_0_6_7_x64-linux_MyOrgLtd.xml")
    val attestation_link: String?

    @Schema(example = "https://github.com/adoptium/temurin-attestations/blob/main/21/jdk_21_0_6_7_x64-linux_MyOrgLtd.xml.sign.pub")
    val attestation_public_signing_key_link: String?

    @JsonCreator
    constructor(
        id: String,
        filename: String,
        featureVersion: Int,
        release_name: String?,
        os: OperatingSystem,
        architecture: Architecture,
        image_type: ImageType,
        jvm_impl: JvmImpl,
        vendor: Vendor,
        target_checksum: String?,
        assessor_org: String?,
        assessor_affirmation: String?,
        assessor_claim_predicate: String?,
        attestation_link: String?,
        attestation_public_signing_key_link: String?,
        committedDate: Instant?
    ) {
        this.id = id
        this.filename = filename
        this.featureVersion = featureVersion
        this.release_name = release_name
        this.os = os
        this.architecture = architecture
        this.image_type = image_type
        this.jvm_impl = jvm_impl
        this.vendor = vendor
        this.target_checksum = target_checksum
        this.assessor_org = assessor_org
        this.assessor_affirmation = assessor_affirmation
        this.assessor_claim_predicate = assessor_claim_predicate
        this.attestation_link = attestation_link
        this.attestation_public_signing_key_link = attestation_public_signing_key_link
        this.committedDate = committedDate
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Attestation

        if (id != other.id) return false
        if (filename != other.filename) return false
        if (featureVersion != other.featureVersion) return false
        if (release_name != other.release_name) return false
        if (os != other.os) return false
        if (architecture != other.architecture) return false
        if (image_type != other.image_type) return false
        if (jvm_impl != other.jvm_impl) return false
        if (vendor != other.vendor) return false
        if (target_checksum != other.target_checksum) return false
        if (assessor_org != other.assessor_org) return false
        if (assessor_affirmation != other.assessor_affirmation) return false
        if (assessor_claim_predicate != other.assessor_claim_predicate) return false
        if (attestation_link != other.attestation_link) return false
        if (attestation_public_signing_key_link != other.attestation_public_signing_key_link) return false
        if (committedDate != other.committedDate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = featureVersion.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + release_name.hashCode()
        result = 31 * result + os.hashCode()
        result = 31 * result + architecture.hashCode()
        result = 31 * result + image_type.hashCode()
        result = 31 * result + jvm_impl.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + target_checksum.hashCode()
        result = 31 * result + assessor_org.hashCode()
        result = 31 * result + assessor_affirmation.hashCode()
        result = 31 * result + assessor_claim_predicate.hashCode()
        result = 31 * result + attestation_link.hashCode()
        result = 31 * result + attestation_public_signing_key_link.hashCode()
        result = 31 * result + committedDate.hashCode()
        return result
    }

    override fun toString(): String {
        return "Attestation(id='$id', filename='$filename', featureVersion='$featureVersion', release_name='$release_name', os='$os', architecture='$architecture', image_type='$image_type', jvm_impl='$jvm_impl', vendor='$vendor'" +
                           "assessor_org='$assessor_org', assessor_affirmation='$assessor_affirmation', assessor_claim_predicate.hashCode='$assessor_claim_predicate.hashCode', " +
                           "target_checksum='$target_checksum', "+
                           "attestation_link='$attestation_link', attestation_public_signing_key_link='$attestation_public_signing_key_link', committedDate='$committedDate')"
    }
}
