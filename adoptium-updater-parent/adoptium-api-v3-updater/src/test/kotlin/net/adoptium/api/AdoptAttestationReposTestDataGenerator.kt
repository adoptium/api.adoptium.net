package net.adoptium.api

import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import java.util.*

object AdoptAttestationReposTestDataGenerator {

    var rand: Random = Random(1)
    private val TEST_RESOURCES = listOf(
        Attestation(randomString("attestation id"),
                    randomString("commitResourcePath"),
                    rand.nextInt(20)+8,
                    randomString("releaseName"),
                    OperatingSystem.linux,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum"),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"),
                    randomString("assessor_claim_predicate"),
                    randomString("attestation_link"),
                    randomString("attestation_public_signing_key_link")),
        Attestation(randomString("attestation id"),
                    randomString("commitResourcePath"),
                    rand.nextInt(20)+8,
                    randomString("releaseName"),
                    OperatingSystem.linux,
                    Architecture.x32, 
                    ImageType.jdk, 
                    JvmImpl.hotspot,
                    Vendor.eclipse, 
                    randomString("target_checksum"),
                    randomString("assessor_org"), 
                    randomString("assessor_affirmation"), 
                    randomString("assessor_claim_predicate"),
                    randomString("attestation_link"), 
                    randomString("attestation_public_signing_key_link")),
        Attestation(randomString("attestation id"),
                    randomString("commitResourcePath"),
                    rand.nextInt(20)+8,
                    randomString("releaseName"),
                    OperatingSystem.mac,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum"),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"), 
                    randomString("assessor_claim_predicate"),
                    randomString("attestation_link"),
                    randomString("attestation_public_signing_key_link")),
        Attestation(randomString("attestation id"),
                    randomString("commitResourcePath"),
                    rand.nextInt(20)+8,
                    randomString("releaseName"),
                    OperatingSystem.windows,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum"),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"),
                    randomString("assessor_claim_predicate"),
                    randomString("attestation_link"),
                    randomString("attestation_public_signing_key_link"))
    )

    fun generate(): AdoptAttestationRepos {
        rand = Random(1)

        val repo = AdoptAttestationRepos(TEST_RESOURCES)

        return repo
    }

    fun randomString(prefix: String): String {
        return prefix + ": " + rand.nextInt().toString()
    }
}
