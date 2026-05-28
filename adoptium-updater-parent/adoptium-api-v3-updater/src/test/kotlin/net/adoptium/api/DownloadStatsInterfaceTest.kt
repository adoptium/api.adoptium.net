package net.adoptium.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.UpdatableVersionSupplierStub
import net.adoptium.api.v3.DownloadStatsInterface
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.JvmImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class DownloadStatsInterfaceTest {

    @Test
    fun `package stats should sum delta entries across minutes`() {
        runBlocking {
            val apiPersistenceMock = mockk<ApiPersistence>()
            val baseTime = TimeSource.now()

            coEvery { apiPersistenceMock.getLatestAllDockerStats() } returns emptyList()
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(any()) } returns null
            coEvery { apiPersistenceMock.getAggregatedPackageStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns listOf(
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 1000, 17),
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 500, 21)
            )

            val downloadStatsInterface = DownloadStatsInterface(apiPersistenceMock, UpdatableVersionSupplierStub())
            val stats = downloadStatsInterface.getTotalDownloadStats()

            assertEquals(1500L, stats.total_downloads.package_downloads)
            assertEquals(1000L, stats.package_downloads[17])
            assertEquals(500L, stats.package_downloads[21])
        }
    }

    @Test
    fun `package stats with mixed github and docker should aggregate all sources`() {
        runBlocking {
            val apiPersistenceMock = mockk<ApiPersistence>()
            val baseTime = TimeSource.now()

            // UpdatableVersionSupplierStub.getAllVersions() returns [8, 10, 11, 12, 18]
            coEvery { apiPersistenceMock.getLatestAllDockerStats() } returns listOf(
                DockerDownloadStatsDbEntry(baseTime, 500, "eclipse-temurin", null, null)
            )
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(any()) } returns null
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(8) } returns
                GitHubDownloadStatsDbEntry(baseTime, 200, mapOf(JvmImpl.hotspot to 200L), 8)
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(11) } returns
                GitHubDownloadStatsDbEntry(baseTime, 100, mapOf(JvmImpl.hotspot to 100L), 11)
            coEvery { apiPersistenceMock.getAggregatedPackageStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns listOf(
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 1000, 17),
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 100, 21)
            )

            val downloadStatsInterface = DownloadStatsInterface(apiPersistenceMock, UpdatableVersionSupplierStub())
            val stats = downloadStatsInterface.getTotalDownloadStats()

            // Package: 1000(v17) + 100(v21) = 1100
            assertEquals(1000L, stats.package_downloads[17])
            assertEquals(100L, stats.package_downloads[21])
            // GitHub: 200(v8) + 100(v11) = 300
            // Docker: 500
            // Total: 300 + 500 + 1100 = 1900
            assertEquals(1900L, stats.total_downloads.total)
        }
    }

    @Test
    fun `package stats should return zero when no entries exist`() {
        runBlocking {
            val apiPersistenceMock = mockk<ApiPersistence>()

            coEvery { apiPersistenceMock.getLatestAllDockerStats() } returns emptyList()
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(any()) } returns null
            coEvery { apiPersistenceMock.getAggregatedPackageStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns emptyList()

            val downloadStatsInterface = DownloadStatsInterface(apiPersistenceMock, UpdatableVersionSupplierStub())
            val stats = downloadStatsInterface.getTotalDownloadStats()

            assertEquals(0L, stats.total_downloads.package_downloads)
        }
    }

    @Test
    fun `package stats should include cumulative baseline added to tracked downloads`() {
        runBlocking {
            val apiPersistenceMock = mockk<ApiPersistence>()
            val baseTime = TimeSource.now()

            coEvery { apiPersistenceMock.getLatestAllDockerStats() } returns emptyList()
            coEvery { apiPersistenceMock.getLatestGithubStatsForFeatureVersion(any()) } returns null
            coEvery { apiPersistenceMock.getAggregatedPackageStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns listOf(
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 1000, 17),
                CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 500, 21)
            )

            // Currently the baseline placeholder is empty; verify breakdown matches tracked stats.
            // This guards the cumulative-sum semantics (baseline + tracked) and will continue to
            // pass once a non-empty baseline is populated for additional feature versions.
            val downloadStatsInterface = DownloadStatsInterface(apiPersistenceMock, UpdatableVersionSupplierStub())
            val stats = downloadStatsInterface.getTotalDownloadStats()

            val baseline = DownloadStatsInterface.PACKAGE_DOWNLOAD_BASELINE_2026_05_28
            val expected17 = 1000L + (baseline[17] ?: 0L)
            val expected21 = 500L + (baseline[21] ?: 0L)
            val expectedTotal = expected17 + expected21 + baseline.filterKeys { it != 17 && it != 21 }.values.sum()

            assertEquals(expected17, stats.package_downloads[17])
            assertEquals(expected21, stats.package_downloads[21])
            assertEquals(expectedTotal, stats.total_downloads.package_downloads)
        }
    }
}
