package net.adoptium.api

import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Cdxa
import java.util.*
import java.time.Instant

object AdoptCdxaReposTestDataGenerator {

    var rand: Random = Random(1)
    private val TEST_RESOURCES = listOf(
        Cdxa(randomString("cdxa id"),
                    "21/jdk-21.0.5+6/cdxa_jdk-21.0.5+6.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.5+6.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.5+6.xml.sig",
                    Instant.now()),
        Cdxa(randomString("cdxa id"),
                    "21/jdk-21.0.5+6/cdxa_jdk-21.0.5+6_other.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.5+6_other.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.5+6_other.xml.sig",
                    Instant.now()),
        Cdxa(randomString("cdxa id"),
                    "21/jdk-21.0.6+8/cdxa_jdk-21.0.6+8.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.6+8.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/21/cdxa_jdk-21.0.6+8.xml.sig",
                    Instant.now()),
        Cdxa(randomString("cdxa id"),
                    "23/jdk-23.0.1+6/cdxa_jdk-23.0.1+6.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/23/cdxa_jdk-23.0.1+6.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/23/cdxa_jdk-23.0.1+6.xml.sig",
                    Instant.now()),
        Cdxa(randomString("cdxa id"),
                    "24/jdk-24.0.2+12/cdxa_jdk-24.0.2+12.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/24/cdxa_jdk-24.0.2+12.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/24/cdxa_jdk-24.0.2+12.xml.sig",
                    Instant.now()),
        Cdxa(randomString("cdxa id"),
                    "11/jdk-11.0.21+8/cdxa_jdk-11.0.21+8.xml",
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
                    "https://github.com/adoptium/temurin-cdxa/blob/main/11/cdxa_jdk-11.0.21+8.xml",
                    "https://github.com/adoptium/temurin-cdxa/blob/main/11/cdxa_jdk-11.0.21+8.xml.sig",
                    Instant.now())
    )

    fun generate(): AdoptCdxaRepos {
        rand = Random(1)

        val repo = AdoptCdxaRepos(TEST_RESOURCES)

        return repo
    }

    fun randomString(prefix: String): String {
        return prefix + ": " + rand.nextInt().toString()
    }
}
