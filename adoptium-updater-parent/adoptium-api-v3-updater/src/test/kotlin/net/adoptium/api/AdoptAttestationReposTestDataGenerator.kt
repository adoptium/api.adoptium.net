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
import java.time.Instant

object AdoptAttestationReposTestDataGenerator {

    var rand: Random = Random(1)
    private val TEST_RESOURCES = listOf(
        Attestation(randomString("attestation id"),
                    "21/jdk-21.0.5+6/attestation_jdk-21.0.5+6.xml",
                    21,
                    "jdk-21.0.5+6",
                    OperatingSystem.linux,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    "123456ABCDEF",
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"),
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.5+6.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.5+6.xml.sign.pub",
                    Instant.now()),
        Attestation(randomString("attestation id"),
                    "21/jdk-21.0.5+6/attestation_jdk-21.0.5+6_other.xml",
                    21,
                    "jdk-21.0.5+6",
                    OperatingSystem.linux,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    "123456ABCDEF",
                    randomString("assessor_org_other"),
                    randomString("assessor_affirmation_other"),
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.5+6_other.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.5+6_other.xml.sign.pub",
                    Instant.now()),
        Attestation(randomString("attestation id"),
                    "21/jdk-21.0.6+8/attestation_jdk-21.0.6+8.xml",
                    21,
                    "jdk-21.0.6+8",
                    OperatingSystem.linux,
                    Architecture.aarch64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum").uppercase(),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"),
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.6+8.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/21/attestation_jdk-21.0.6+8.xml.sign.pub",
                    Instant.now()),
        Attestation(randomString("attestation id"),
                    "23/jdk-23.0.1+6/attestation_jdk-23.0.1+6.xml",
                    23,
                    "jdk-23.0.1+6",
                    OperatingSystem.linux,
                    Architecture.x32, 
                    ImageType.jdk, 
                    JvmImpl.hotspot,
                    Vendor.eclipse, 
                    randomString("target_checksum").uppercase(),
                    randomString("assessor_org"), 
                    randomString("assessor_affirmation"), 
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/23/attestation_jdk-23.0.1+6.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/23/attestation_jdk-23.0.1+6.xml.sign.pub",
                    Instant.now()),
        Attestation(randomString("attestation id"),
                    "24/jdk-24.0.2+12/attestation_jdk-24.0.2+12.xml",
                    24,
                    "jdk-24.0.2+12",
                    OperatingSystem.mac,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum").uppercase(),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"), 
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/24/attestation_jdk-24.0.2+12.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/24/attestation_jdk-24.0.2+12.xml.sign.pub",
                    Instant.now()),
        Attestation(randomString("attestation id"),
                    "11/jdk-11.0.21+8/attestation_jdk-11.0.21+8.xml",
                    11,
                    "jdk-11.0.21+8",
                    OperatingSystem.windows,
                    Architecture.x64,
                    ImageType.jdk,
                    JvmImpl.hotspot,
                    Vendor.eclipse,
                    randomString("target_checksum").uppercase(),
                    randomString("assessor_org"),
                    randomString("assessor_affirmation"),
                    randomString("assessor_claim_predicate"),
                    "https://github.com/adoptium/temurin-attestations/blob/main/11/attestation_jdk-11.0.21+8.xml",
                    "https://github.com/adoptium/temurin-attestations/blob/main/11/attestation_jdk-11.0.21+8.xml.sign.pub",
                    Instant.now())
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
