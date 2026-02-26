package net.adoptium.api.v3.stats.cloudflare

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CloudflareStatsCalculatorTest {

    @ParameterizedTest(name = "extractVersionFromPath(\"{0}\") should return {1}")
    @CsvSource(
        // APK patterns
        "/artifactory/apk/alpine/main/aarch64/temurin-17-jdk-17.0.15_p6-r0.apk, 17",
        "/artifactory/apk/alpine/main/aarch64/temurin-21-jdk-21.0.10_p7-r0.apk, 21",
        "/artifactory/apk/alpine/main/x86_64/temurin-8-jdk-8.482.08-r0.apk, 8",
        "/artifactory/apk/alpine/main/x86_64/temurin-25-jre-25.0.2_p10-r0.apk, 25",
        "/artifactory/apk/alpine/main/x86_64/temurin-11-jre-11.0.30_p7-r0.apk, 11",
        // DEB patterns
        "/artifactory/deb/pool/main/t/temurin-11/temurin-11-jdk_11.0.13.0.0%2b8-1_amd64.deb, 11",
        "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.1.0.0+12-1_amd64.deb, 17",
        "/artifactory/deb/pool/main/t/temurin-21/temurin-21-jdk_21.0.0.0.0%2b35_amd64.deb, 21",
        "/artifactory/deb/pool/main/t/temurin-8/temurin-8-jdk_8.0.312.0.0+7-1_amd64.deb, 8",
        "/artifactory/deb/pool/main/t/temurin-25/temurin-25-jre_25.0.2.0.0%2B10-1_amd64.deb, 25",
        "/artifactory/deb/pool/main/t/temurin-11/temurin-11-jdk_11.0.30.0.0%2b7-0_amd64.deb, 11",
        "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jre_17.0.18.0.0%2b8-0_amd64.deb, 17",
        // RPM patterns
        "/artifactory/rpm/amazonlinux/2/aarch64/Packages/temurin-11-jdk-11.0.30.0.0.7-0.aarch64.rpm, 11",
        "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.18.0.0.8-1.x86_64.rpm, 17",
        "/artifactory/rpm/fedora/36/x86_64/Packages/temurin-21-jdk-21.0.10.0.0.7-1.x86_64.rpm, 21",
        "/artifactory/rpm/oraclelinux/8/x86_64/Packages/temurin-8-jdk-8.0.482.0.0.8-0.x86_64.rpm, 8",
        "/artifactory/rpm/rhel/10/x86_64/Packages/temurin-25-jdk-25.0.2.0.0.10-1.x86_64.rpm, 25",
        "/artifactory/rpm/centos/7/aarch64/Packages/temurin-21-jre-21.0.10.0.0.7-0.aarch64.rpm, 21",
        // Source RPM patterns
        "/artifactory/rpm/centos/7/source/Packages/temurin-21-jdk-21.0.7.0.0.6-0.riscv64.src.rpm, 21",
        "/artifactory/rpm/oraclelinux/8/source/Packages/temurin-11-jre-11.0.26.0.0.4-1.ppc64le.src.rpm, 11",
        "/artifactory/rpm/rhel/10/source/Packages/temurin-17-jdk-17.0.18.0.0.8-1.x86_64.src.rpm, 17"
    )
    fun `extractVersionFromPath should extract version from valid paths`(path: String, expectedVersion: Int) {
        assertEquals(expectedVersion, CloudflareStatsCalculator.extractVersionFromPath(path))
    }

    @ParameterizedTest(name = "extractVersionFromPath(\"{0}\") should return null")
    @ValueSource(strings = [
        "",
        "/some/random/path",
        "/temurin-/file.deb",
        "/temurin-abc/file.deb",
        "/deb/pool/main/t/other-package/file.deb"
    ])
    fun `extractVersionFromPath should return null for invalid paths`(path: String) {
        assertNull(CloudflareStatsCalculator.extractVersionFromPath(path))
    }

    @Test
    fun `updateDb should aggregate downloads by date and version`() = runBlocking {
        
        val mockClient = mockk<CloudflareClient>()
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptAttestationRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        val testDate = LocalDate.of(2024, 1, 15)
        val response = CloudflareResponse(
            data = listOf(
                // Version 17 entries - should aggregate to 150
                CloudflarePackageStats(testDate, 100, "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.10_amd64.deb"),
                CloudflarePackageStats(testDate, 50, "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.10.x86_64.rpm"),
                // Version 21 entries - should aggregate to 300
                CloudflarePackageStats(testDate, 200, "/artifactory/deb/pool/main/t/temurin-21/temurin-21-jdk_21.0.2_amd64.deb"),
                CloudflarePackageStats(testDate, 100, "/artifactory/apk/alpine/main/x86_64/temurin-21-jdk-21.0.2.apk"),
                // Invalid entry - should be skipped
                CloudflarePackageStats(testDate, 999, "/invalid/path"),
                // Different date - should be separate entry
                CloudflarePackageStats(testDate.plusDays(1), 75, "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.10_amd64.deb")
            )
        )

        coEvery { mockClient.fetchDownloadStats(any(), any()) } returns response

        calculator.updateDb()

        val startTime = testDate.atStartOfDay(ZoneId.of("UTC")).minusDays(1)
        val endTime = testDate.plusDays(2).atStartOfDay(ZoneId.of("UTC"))
        val savedStats = database.getPackageStats(startTime, endTime)

        assertEquals(3, savedStats.size)

        // Find stats for version 17 on first date
        val version17Stats = savedStats.find { it.feature_version == 17 && it.date.toLocalDate() == testDate }
        assertNotNull(version17Stats)
        assertEquals(150, version17Stats!!.downloads)

        // Find stats for version 21 on first date
        val version21Stats = savedStats.find { it.feature_version == 21 && it.date.toLocalDate() == testDate }
        assertNotNull(version21Stats)
        assertEquals(300, version21Stats!!.downloads)

        // Find stats for version 17 on second date
        val version17NextDayStats = savedStats.find { it.feature_version == 17 && it.date.toLocalDate() == testDate.plusDays(1) }
        assertNotNull(version17NextDayStats)
        assertEquals(75, version17NextDayStats!!.downloads)

        coVerify(exactly = 1) { mockClient.fetchDownloadStats(any(), any()) }
    }

    @Test
    fun `updateDb should handle empty response gracefully`() = runBlocking {
        
        val mockClient = mockk<CloudflareClient>()
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptAttestationRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        coEvery { mockClient.fetchDownloadStats(any(), any()) } returns CloudflareResponse(data = emptyList())

        
        calculator.updateDb()

        
        val savedStats = database.getPackageStats(
            ZonedDateTime.now().minusDays(7),
            ZonedDateTime.now().plusDays(1)
        )
        assertTrue(savedStats.isEmpty())
    }

    @Test
    fun `updateDb should skip entries with unparseable versions`() = runBlocking {
        
        val mockClient = mockk<CloudflareClient>()
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptAttestationRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        val testDate = LocalDate.of(2024, 1, 15)
        val response = CloudflareResponse(
            data = listOf(
                CloudflarePackageStats(testDate, 100, "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.18.0.0.8-1.x86_64.rpm"),
                CloudflarePackageStats(testDate, 50, "/invalid/path1"),
                CloudflarePackageStats(testDate, 200, "/artifactory/deb/pool/main/t/temurin-11/temurin-11-jdk_11.0.30.0.0%2b7-0_amd64.deb"),
                CloudflarePackageStats(testDate, 75, "/another/invalid"),
                CloudflarePackageStats(testDate, 150, "/artifactory/apk/alpine/main/x86_64/temurin-8-jdk-8.482.08-r0.apk")
            )
        )

        coEvery { mockClient.fetchDownloadStats(any(), any()) } returns response

        
        calculator.updateDb()

        
        val savedStats = database.getPackageStats(
            testDate.minusDays(1).atStartOfDay(ZoneId.of("UTC")),
            testDate.plusDays(1).atStartOfDay(ZoneId.of("UTC"))
        )

        assertEquals(3, savedStats.size)
        assertEquals(100, savedStats.find { it.feature_version == 17 }?.downloads)
        assertEquals(200, savedStats.find { it.feature_version == 11 }?.downloads)
        assertEquals(150, savedStats.find { it.feature_version == 8 }?.downloads)
    }
}
