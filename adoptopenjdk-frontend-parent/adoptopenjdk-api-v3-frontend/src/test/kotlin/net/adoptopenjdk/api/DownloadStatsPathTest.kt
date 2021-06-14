package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.v3.DownloadStatsInterface
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.DownloadDiff
import net.adoptopenjdk.api.v3.models.DownloadStats
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.StatsSource
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.routes.stats.DownloadStatsResource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.format.DateTimeFormatter
import javax.ws.rs.BadRequestException
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

@ExtendWith(value = [DbExtension::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DownloadStatsPathTest : FrontendTest() {

    private val apiDataStore: APIDataStore = ApiDataStoreStub()

    private val apiPersistence: ApiPersistence = InMemoryApiPersistence(
        AdoptReposTestDataGenerator.generate()
    )

    private val downloadStatsResource: DownloadStatsResource = createDownloadStatsResource(apiDataStore, apiPersistence)

    private fun createDownloadStatsResource(apiDataStore: APIDataStore, apiPersistence: ApiPersistence): DownloadStatsResource {
        return runBlocking {
            val persistance = apiPersistence

            persistance.addDockerDownloadStatsEntries(
                createDockerStatsWithRepoName()
            )

            persistance.addGithubDownloadStatsEntries(
                createGithubData()
            )

            val downloadStatsResource = DownloadStatsResource(apiDataStore, DownloadStatsInterface(persistance))
            downloadStatsResource
        }
    }

    private fun createGithubData(): List<GitHubDownloadStatsDbEntry> {
        return listOf(
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(15),
                0,
                null,
                11
            ),
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(10),
                10,
                mapOf(JvmImpl.hotspot to 10L),
                11
            ),
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(5),
                20,
                mapOf(JvmImpl.hotspot to 16L, JvmImpl.openj9 to 4L),
                8
            ),
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(1),
                40,
                mapOf(JvmImpl.hotspot to 30L, JvmImpl.openj9 to 10L),
                11
            ),
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(1).minusMinutes(1),
                25,
                mapOf(JvmImpl.hotspot to 20L, JvmImpl.openj9 to 5L),
                8
            ),
            GitHubDownloadStatsDbEntry(
                TimeSource.now().minusDays(1),
                30,
                mapOf(JvmImpl.hotspot to 20L, JvmImpl.openj9 to 10L),
                8
            )
        )
    }

    private fun createDockerStatsWithRepoName(): List<DockerDownloadStatsDbEntry> {
        return listOf(
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(15),
                0,
                "b-repo-name",
                null,
                null
            ),
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(10),
                20,
                "a-repo-name",
                11,
                JvmImpl.openj9
            ),
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(5),
                30,
                "b-repo-name",
                8,
                JvmImpl.hotspot
            ),
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(1),
                40,
                "b-repo-name",
                11,
                JvmImpl.hotspot
            ),
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(1).minusMinutes(1),
                50,
                "a-repo-name",
                8,
                JvmImpl.openj9
            ),
            DockerDownloadStatsDbEntry(
                TimeSource.now().minusDays(1),
                60,
                "a-repo-name",
                8,
                JvmImpl.openj9
            )
        )
    }

    @Test
    fun totalDownloadReturnsSaneData() {
        val stats = downloadStatsResource
            .getTotalDownloadStats()
            .toCompletableFuture()
            .get().entity as DownloadStats

        assertEquals(170L, stats.total_downloads.total)
        assertEquals(100L, stats.total_downloads.docker_pulls)
        assertEquals(70L, stats.total_downloads.github_downloads)
        assertEquals(30L, stats.github_downloads[8])
        assertEquals(40L, stats.github_downloads[11])
        assertEquals(60L, stats.docker_pulls["a-repo-name"])
        assertEquals(40L, stats.docker_pulls["b-repo-name"])
    }

    @Test
    fun totalVersionReturnsSaneData() {
        val stats = downloadStatsResource.getTotalDownloadStats(8)
        assertTrue { return@assertTrue stats.isNotEmpty() && !stats.containsValue(0L) }
    }

    @Test
    fun badTotalVersionReturnsSaneData() {
        assertThrows<BadRequestException> {
            downloadStatsResource.getTotalDownloadStats(101)
        }
    }

    @Test
    fun totalTagReturnsSaneData() {
        runBlocking {
            val releases = getReleases()

            val release = releases
                .filter { it.vendor == Vendor.getDefault() }
                .first()

            val stats = downloadStatsResource.getTotalDownloadStatsForTag(release.version_data.major, release.release_name)
            assertTrue { return@assertTrue stats.isNotEmpty() && !stats.containsValue(0L) }
        }
    }

    @Test
    fun badTotalTagReturnsSaneData() {
        assertThrows<BadRequestException> {
            downloadStatsResource.getTotalDownloadStatsForTag(101, "fooBar")
        }
    }

    @Test
    fun trackingReturnsSaneData() {
        val stats = downloadStatsResource
            .tracking(null, null, null, null, null, null, null)
            .toCompletableFuture()
            .get()
            .entity as List<DownloadDiff>

        assertTrue { stats.size == 3 }
        assertTrue { stats[0].total == 30L }
        assertTrue { stats[0].daily == 6L }
        assertTrue { stats[1].total == 50L }
        assertTrue { stats[1].daily == 4L }
        assertTrue { stats[2].total == 170L }
        assertTrue { stats[2].daily == 30L }
    }

    @Test
    fun trackingFeatureVersionRetrunsSaneData() {
        val stats = downloadStatsResource
            .tracking(null, null, 11, null, null, null, null)
            .toCompletableFuture()
            .get()
            .entity as List<DownloadDiff>

        assertTrue { stats.size == 2 }
        assertTrue { stats[0].total == 30L }
        assertTrue { stats[0].daily == 6L }
        assertTrue { stats[1].total == 80L }
        assertTrue { stats[1].daily == 5L }
    }

    @Test
    fun trackingJvmImplRetrunsSaneData() {
        val stats = downloadStatsResource
            .tracking(null, null, null, null, "hotspot", null, null)
            .toCompletableFuture()
            .get()
            .entity as List<DownloadDiff>

        assertTrue { stats.size == 2 }
        assertTrue { stats[0].total == 46L }
        assertTrue { stats[0].daily == 7L }
        assertTrue { stats[1].total == 90L }
        assertTrue { stats[1].daily == 11L }
    }

    @Test
    fun trackingDockerRepoRetrunsSaneData() {
        val stats = downloadStatsResource
            .tracking(null, StatsSource.dockerhub, null, "a-repo-name", null, null, null)
            .toCompletableFuture()
            .get()
            .entity as List<DownloadDiff>

        assertTrue { stats.size == 1 }
        assertTrue { stats[0].total == 60L }
        assertTrue { stats[0].daily == 4L }
    }

    @Test
    fun dateRangeFilterWithStartAndEndIsCorrect() {
        requestStats(
            TimeSource.date().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE),
            TimeSource.date().minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE),
            null
        ) { stats ->
            assertTrue { stats.size == 1 }
            assertTrue { stats[0].total == 50L }
            assertTrue { stats[0].daily == 4L }
        }
    }

    @Test
    fun dateRangeFilterWithEndIsCorrect() {
        requestStats(
            null,
            TimeSource.date().minusDays(2).format(DateTimeFormatter.ISO_DATE),
            null
        ) { stats ->
            assertTrue { stats.size == 2 }
            assertTrue { stats[0].total == 30L }
            assertTrue { stats[0].daily == 6L }
            assertTrue { stats[1].total == 50L }
            assertTrue { stats[1].daily == 4L }
        }
    }

    @Test
    fun dateRangeFilterWithEndAndDayIsCorrect() {
        requestStats(
            null,
            TimeSource.date().format(DateTimeFormatter.ISO_DATE),
            1
        ) { stats ->
            assertTrue { stats.size == 1 }
            assertTrue { stats[0].total == 170L }
            assertTrue { stats[0].daily == 30L }
        }
    }

    @Test
    fun throwsOnABadDate() {
        assertFails {
            requestStats(
                null,
                "foo",
                1
            ) { stats ->
                assertTrue { stats.size == 1 }
                assertTrue { stats[0].total == 170L }
                assertTrue { stats[0].daily == 30L }
            }
        }
    }

    private fun requestStats(from: String?, to: String?, days: Int?, check: (List<DownloadDiff>) -> Unit) {
        val stats = downloadStatsResource
            .tracking(days, null, null, null, null, from, to)
            .toCompletableFuture()
            .get()
            .entity as List<DownloadDiff>

        check(stats)
    }
}
