package net.adoptopenjdk.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.v3.DownloadStatsInterface
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.HttpClientFactory
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.StatsSource
import net.adoptopenjdk.api.v3.stats.dockerstats.DockerStats
import net.adoptopenjdk.api.v3.stats.dockerstats.DockerStatsInterface
import net.adoptopenjdk.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.Assert
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertEquals

@AddPackages(value = [DockerStatsInterface::class, DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class DockerStatsInterfaceTest : BaseTest() {
    @Test
    fun dbEntryIsCreated(defaultUpdaterHtmlClient: DefaultUpdaterHtmlClient) {
        runBlocking {
            val apiPersistence = InMemoryApiPersistence(adoptRepos)
            val dockerStatsInterface = DockerStatsInterfaceFactory(apiPersistence, defaultUpdaterHtmlClient).get()

            dockerStatsInterface.updateDb()

            val stats = apiPersistence.getLatestAllDockerStats()
            Assert.assertTrue(stats.size > 0)
        }
    }

    @Test
    fun onlyLastStatEntryPerDayIsRead() {
        runBlocking {
            val apiPersistanceMock = mockk<ApiPersistence>()

            val baseTime = TimeSource.date().minusDays(1).atStartOfDay().plusHours(12).atZone(TimeSource.ZONE)

            coEvery { apiPersistanceMock.getGithubStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns listOf(
                GitHubDownloadStatsDbEntry(baseTime.minusDays(11).plusMinutes(10), 46, mapOf(JvmImpl.hotspot to 28L, JvmImpl.openj9 to 30L), 8),
                GitHubDownloadStatsDbEntry(baseTime.minusDays(5).plusMinutes(10), 70, mapOf(JvmImpl.hotspot to 40L, JvmImpl.openj9 to 30L), 8),
                GitHubDownloadStatsDbEntry(baseTime.minusDays(1).plusMinutes(10), 80, mapOf(JvmImpl.hotspot to 50L, JvmImpl.openj9 to 30L), 8),
                GitHubDownloadStatsDbEntry(baseTime.minusMinutes(15), 20, mapOf(JvmImpl.hotspot to 15L, JvmImpl.openj9 to 5L), 9),
                GitHubDownloadStatsDbEntry(baseTime.minusMinutes(10), 90, mapOf(JvmImpl.hotspot to 80L, JvmImpl.openj9 to 10L), 8),
                GitHubDownloadStatsDbEntry(baseTime, 100, mapOf(JvmImpl.hotspot to 40L, JvmImpl.openj9 to 60L), 8)
            )

            coEvery { apiPersistanceMock.getDockerStats(any<ZonedDateTime>(), any<ZonedDateTime>()) } returns listOf(
                DockerDownloadStatsDbEntry(baseTime.minusDays(5).plusMinutes(20), 50, "a-stats-repo-2", 8, JvmImpl.hotspot),
                DockerDownloadStatsDbEntry(baseTime.minusDays(1).plusMinutes(20), 80, "a-stats-repo", 12, JvmImpl.openj9),
                DockerDownloadStatsDbEntry(baseTime.minusMinutes(10), 90, "a-stats-repo", 14, JvmImpl.openj9),
                DockerDownloadStatsDbEntry(baseTime.minusMinutes(5), 20, "a-different-stats-repo", 11, JvmImpl.hotspot),
                DockerDownloadStatsDbEntry(baseTime, 100, "a-stats-repo", 8, JvmImpl.hotspot)
            )

            val downloadStatsInterface = DownloadStatsInterface(apiPersistanceMock)

            var stats = downloadStatsInterface.getTrackingStats(10, source = StatsSource.github, featureVersion = 8)
            assertEquals(70, stats[0].total)
            assertEquals(80, stats[1].total)
            assertEquals(100, stats[2].total)
            assertEquals(4, stats[0].daily)
            assertEquals(2, stats[1].daily)
            assertEquals(20, stats[2].daily)
            stats = downloadStatsInterface.getTrackingStats(10, source = StatsSource.github)
            assertEquals(70, stats[0].total)
            assertEquals(80, stats[1].total)
            assertEquals(120, stats[2].total)
            assertEquals(2, stats[1].daily)
            assertEquals(40, stats[2].daily)

            stats = downloadStatsInterface.getTrackingStats(10, source = StatsSource.dockerhub, dockerRepo = "a-stats-repo")
            assertEquals(100, stats[0].total)
            stats = downloadStatsInterface.getTrackingStats(10, source = StatsSource.dockerhub)
            assertEquals(80, stats[0].total)

            stats = downloadStatsInterface.getTrackingStats(10)
            assertEquals(120, stats[0].total)
            assertEquals(160, stats[1].total)
            assertEquals(240, stats[2].total)
        }
    }

    @Test
    fun testGetOpenjdkVersionFromString() {
        runBlocking {
            assertEquals(11, DockerStats.getOpenjdkVersionFromString("openjdk11"))
            assertEquals(8, DockerStats.getOpenjdkVersionFromString("openjdk8-openj9"))
            assertEquals(12, DockerStats.getOpenjdkVersionFromString("maven-openjdk12"))
            assertEquals(14, DockerStats.getOpenjdkVersionFromString("maven-openjdk14-openj9"))

            assertEquals(null, DockerStats.getOpenjdkVersionFromString("official"))
        }
    }
}
