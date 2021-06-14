package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.v3.DownloadStatsInterface
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.MonthlyDownloadDiff
import net.adoptopenjdk.api.v3.models.StatsSource
import net.adoptopenjdk.api.v3.routes.stats.DownloadStatsResource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertTrue

@ExtendWith(value = [DbExtension::class])
class MonthlyStatsPathTest : FrontendTest() {

    private val downloadStatsResource: DownloadStatsResource = initDownloadStatsResource()

    companion object {

        fun initDownloadStatsResource(): DownloadStatsResource {
            val api = initApi()
            return DownloadStatsResource(ApiDataStoreStub(), DownloadStatsInterface(api))
        }

        fun initApi(): ApiPersistence {
            val api = InMemoryApiPersistence(
                AdoptReposTestDataGenerator.generate()
            )

            runBlocking {
                api.addDockerDownloadStatsEntries(
                    createDockerStatsWithRepoName()
                )
                api.addGithubDownloadStatsEntries(createGithubData())
            }
            return api
        }

        private fun createGithubData(): List<GitHubDownloadStatsDbEntry> {
            return listOf(
                GitHubDownloadStatsDbEntry(
                    TimeSource.now(),
                    800,
                    mapOf(JvmImpl.hotspot to 600L, JvmImpl.openj9 to 200L),
                    11
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(1).withDayOfMonth(26),
                    600,
                    mapOf(JvmImpl.hotspot to 450L, JvmImpl.openj9 to 150L),
                    8
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(1).withDayOfMonth(4),
                    400,
                    mapOf(JvmImpl.hotspot to 300L, JvmImpl.openj9 to 100L),
                    11
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(2).withDayOfMonth(23),
                    350,
                    mapOf(JvmImpl.hotspot to 275L, JvmImpl.openj9 to 75L),
                    11
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(3).withDayOfMonth(24),
                    200,
                    mapOf(JvmImpl.hotspot to 150L, JvmImpl.openj9 to 50L),
                    8
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(4).withDayOfMonth(19),
                    100,
                    null,
                    8
                ),
                GitHubDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(4).withDayOfMonth(19),
                    100,
                    null,
                    11
                )
            )
        }

        private fun createDockerStatsWithRepoName(): List<DockerDownloadStatsDbEntry> {
            return listOf(
                DockerDownloadStatsDbEntry(
                    TimeSource.now(),
                    600,
                    "a-repo-name",
                    8,
                    JvmImpl.hotspot
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(1).withDayOfMonth(26),
                    500,
                    "b-repo-name",
                    8,
                    JvmImpl.openj9
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(1).withDayOfMonth(4),
                    310,
                    "a-repo-name",
                    11,
                    JvmImpl.openj9
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(2).withDayOfMonth(23),
                    230,
                    "a-repo-name",
                    11,
                    JvmImpl.hotspot
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(3).withDayOfMonth(24),
                    150,
                    "b-repo-name",
                    8,
                    JvmImpl.hotspot
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(4).withDayOfMonth(19),
                    50,
                    "a-repo-name",
                    null,
                    null
                ),
                DockerDownloadStatsDbEntry(
                    TimeSource.now().minusMonths(4).withDayOfMonth(19),
                    50,
                    "b-repo-name",
                    null,
                    null
                )
            )
        }
    }

    @Test
    fun trackingReturnsSaneData() {
        val result = downloadStatsResource.tracking(null, null, null, null, null)
        val stats = result.toCompletableFuture().get().entity as ArrayList<MonthlyDownloadDiff>

        assertTrue(stats.size == 3)
        assertTrue(stats[0].total == 350L)
        assertTrue(stats[0].monthly == 50L)
        assertTrue(stats[1].total == 580L)
        assertTrue(stats[1].monthly == 230L)
        assertTrue(stats[2].total == 1100L)
        assertTrue(stats[2].monthly == 520L)
    }

    @Test
    fun trackingFeatureVersionRetrunsSaneData() {
        runBlocking {
            val result = downloadStatsResource.tracking(null, 8, null, null, null)
            val stats = result.toCompletableFuture().get().entity as ArrayList<MonthlyDownloadDiff>

            assertTrue(stats.size == 2)
            assertTrue(stats[0].total == 350L)
            assertTrue(stats[0].monthly == 250L)
            assertTrue(stats[1].total == 1100L)
            assertTrue(stats[1].monthly == 750L)
        }
    }

    @Test
    fun trackingJvmImplRetrunsSaneData() {
        runBlocking {
            val result = downloadStatsResource.tracking(null, null, null, "hotspot", null)
            val stats = result.toCompletableFuture().get().entity as ArrayList<MonthlyDownloadDiff>

            assertTrue(stats.size == 2)
            assertTrue(stats[0].total == 505L)
            assertTrue(stats[0].monthly == 205L)
            assertTrue(stats[1].total == 450L)
            assertTrue(stats[1].monthly == -55L)
        }
    }

    @Test
    fun trackingDockerRepoRetrunsSaneData() {
        runBlocking {
            val result = downloadStatsResource.tracking(StatsSource.dockerhub, null, "a-repo-name", null, null)
            val stats = result.toCompletableFuture().get().entity as ArrayList<MonthlyDownloadDiff>

            assertTrue(stats.size == 2)
            assertTrue(stats[0].total == 230L)
            assertTrue(stats[0].monthly == 180L)
            assertTrue(stats[1].total == 310L)
            assertTrue(stats[1].monthly == 80L)
        }
    }
}
