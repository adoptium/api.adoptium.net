package net.adoptium.api

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.releaseNotes.AdoptReleaseNotes
import net.adoptium.api.v3.stats.StatsInterface
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

class V3UpdaterEndToEndTest {

    @Test
    fun `old release is copied over`() {

        runBlocking {
            var repo = AdoptReposTestDataGenerator.generate()

            repo = repo.addAll(listOf(Release("11", ReleaseType.ga, "a-link", "jdk-8.0.9999+7", DateTime("2013-02-27T19:35:32Z"), DateTime("2013-02-27T19:35:32Z"), emptyArray(), 1, Vendor.eclipse, VersionData(8, 0, 9999, null, 1, 7, null, ""))))

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }

    @Test
    fun `old snapshot is copied over`() {

        runBlocking {
            var repo = AdoptReposTestDataGenerator.generate()

            repo = repo.addAll(listOf(Release("11", ReleaseType.ea, "a-link", "jdk-8.0.9999+7", DateTime("2013-02-27T19:35:32Z"), DateTime("2013-02-27T19:35:32Z"), emptyArray(), 1, Vendor.eclipse, VersionData(8, 0, 9999, null, 1, 7, null, ""))))

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }

    @Test
    fun `unchanged repo has no updates`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                val releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!))).releases.releases

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size)
        }
    }


    @Test
    fun `new release is added`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { id ->
                if (id.id == "1") {
                    GHRelease(
                        GitHubId("1"),
                        "jdk-11.0.9999+7",
                        true,
                        "2013-02-27T19:35:32Z",
                        "2013-02-27T19:35:32Z",
                        GHAssets(
                            listOf(GHAsset("OpenJDK11U-jdk_x64_linux_hotspot_11.0.2_7.tar.gz", 1L, "the-source-link", 1L, "2013-02-27T19:35:32Z")),
                            PageInfo(false, ""),
                            1
                        ), "1", "/AdoptOpenJDK/openjdk11-binaries/releases/tag/jdk9u-2018-09-27-08-50"
                    )
                } else {
                    null
                }
            }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                var releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!
                )
                )
                ).releases.releases


                if (majorVersion == 11) {
                    releases = releases.plus(GHRelease(GitHubId("1"), "jdk-11.0.9999+7", true, "2013-02-27T19:35:32Z", "2013-02-27T19:35:32Z", GHAssets(listOf(GHAsset("OpenJDK11U-jdk_x64_linux_hotspot_11.0.2_7.tar.gz", 1L, "the-source-link", 1L, "2013-02-27T19:35:32Z"
                    )
                    ), PageInfo(false, ""), 1
                    ), "1", "/AdoptOpenJDK/openjdk11-binaries/releases/tag/jdk9u-2018-09-27-08-50"
                    )
                    )
                }

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)


            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size + 1)
            assertTrue(updatedRepo.repos[11]!!.releases.nodeList.last().release_name == "jdk-11.0.9999+7")
        }
    }

    @Test
    fun `removed release disappears`() {
        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val getById: (GitHubId) -> GHRelease? = { null }

            val getRepository: ((String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository) = { _, repoName, _ ->
                val match = Regex("(\\d+)").find(repoName)!!
                val (version) = match.destructured
                val majorVersion = Integer.parseInt(version)

                var releases = GHSummaryTestDataGenerator.generateGHRepository(AdoptRepos(listOf(repo.repos[majorVersion]!!
                )
                )
                ).releases.releases

                if (majorVersion == 11) {
                    releases = releases.minus(releases.first())
                }

                GHRepository(GHReleases(releases, PageInfo(false, null)))
            }

            val updatedRepo = runUpdateTest(repo, getRepository, getById)

            val removed = repo.repos[11]!!.releases.nodeList.first().release_name

            assertTrue(updatedRepo.allReleases.nodeList.size == repo.allReleases.nodeList.size - 1)
            assertTrue(updatedRepo.allReleases.nodeList.none { it.release_name == removed })
        }
    }

    private fun runUpdateTest(repo: AdoptRepos, getRepository: (String, String, (updatedAt: String, isPrerelease: Boolean) -> Boolean) -> GHRepository, getById: (GitHubId) -> GHRelease?): AdoptRepos {
        val memoryDb = InMemoryApiPersistence(repo)

        val apiDataStore: APIDataStore = APIDataStoreImpl(memoryDb)

        val vs = object : UpdatableVersionSupplier {
            override suspend fun updateVersions() {
            }

            override fun getTipVersion(): Int? {
                return 25
            }

            override fun getLtsVersions(): Array<Int> {
                return arrayOf(8, 11, 17, 21)
            }

            override fun getAllVersions(): Array<Int> {
                return repo.repos.keys.toTypedArray()
            }
        }

        val ghClient = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? {
                TODO("Not yet implemented")
            }
        }

        val updater = V3Updater(
            AdoptReposBuilder(
                AdoptRepositoryImpl(
                    object : GitHubApi {
                        override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
                            return getRepository(owner, repoName, filter)
                        }

                        override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                            return GHSummaryTestDataGenerator.generateGHRepositorySummary(getRepository(owner, repoName) { _, _ -> true })
                        }

                        override suspend fun getReleaseById(id: GitHubId): GHRelease? {

                            val result = getById(id)
                            if (result != null) {
                                return result
                            }

                            return repo.allReleases.nodeList.firstOrNull { it.id == id.id }?.let {
                                return GHRelease(GitHubId(it.id), it.release_name, it.release_type == ReleaseType.ea, it.timestamp.dateTime.toString(), it.updated_at.dateTime.toString(), GHAssets(it.binaries.map { binary ->
                                    GHAsset(binary.`package`.name, binary.`package`.size, binary.`package`.link, binary.`package`.download_count, binary.updated_at.dateTime.toString()
                                    )
                                }, PageInfo(false, null), it.binaries.size
                                ), it.id, "/AdoptOpenJDK/openjdk${it.version_data.major}-binaries/releases/tag/jdk8u-2020-01-09-03-36"
                                )
                            }
                        }
                    },
                    AdoptReleaseMapperFactory(AdoptBinaryMapper(ghClient), ghClient)
                ),
                vs
            ),
            apiDataStore,
            memoryDb,
            object : StatsInterface(mockk(), object : DockerStatsInterfaceFactory(mockk(), mockk()) {
                override fun getDockerStatsInterface(): StatsInterface {
                    return mockk()
                }
            }) {
                override suspend fun updateStats() {
                }

                override suspend fun update(repos: AdoptRepos) {
                }
            },
            ReleaseVersionResolver(vs),
            object : AdoptReleaseNotes(
                mockk(),
                mockk(),
                mockk(),
            ) {
                override suspend fun updateReleaseNotes(adoptRepos: AdoptRepos) {}
            },
            vs
        )

        val updatedRepo = updater.runUpdate(repo, AtomicBoolean(true), mockk())
        return updatedRepo
    }

}
