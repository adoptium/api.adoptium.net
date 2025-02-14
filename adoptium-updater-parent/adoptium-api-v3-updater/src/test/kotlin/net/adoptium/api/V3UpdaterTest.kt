package net.adoptium.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.quarkus.runtime.ApplicationLifecycleManager
import io.quarkus.runtime.Quarkus
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class V3UpdaterTest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @Test
    fun `exit is called when db not present`() {
        runBlocking {
            val apiDataStore: APIDataStore = mockk()
            coEvery { apiDataStore.isConnectedToDb() } returns false

            mockkStatic(Quarkus::class)
            val called = AtomicBoolean(false)
            every { Quarkus.asyncExit(any()) } answers {
                called.set(true)
                ApplicationLifecycleManager.exit(2)
            }

            val updater = V3Updater(
                mockk(),
                apiDataStore,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk()
            )

            updater.run(true)
            assertTrue(called.get())
        }
    }


    @Test
    fun `new update works`() {
        runBlocking {
            val apiDataStore: APIDataStore = mockk()
            coEvery { apiDataStore.isConnectedToDb() } returns false

            mockkStatic(Quarkus::class)
            val called = AtomicBoolean(false)
            every { Quarkus.asyncExit(any()) } answers {
                called.set(true)
                ApplicationLifecycleManager.exit(2)
            }

            val repo = AdoptReposTestDataGenerator.generate()

            val vs = object : UpdatableVersionSupplier {
                override suspend fun updateVersions() {
                }

                override fun getTipVersion(): Int? {
                    TODO("Not yet implemented")
                }

                override fun getLtsVersions(): Array<Int> {
                    TODO("Not yet implemented")
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
                                return GHSummaryTestDataGenerator.generateGHRepository(repo)
                            }

                            override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
                                return GHSummaryTestDataGenerator.generateGHRepositorySummary(GHSummaryTestDataGenerator.generateGHRepository(repo))
                            }

                            override suspend fun getReleaseById(id: GitHubId): GHRelease? {
                                return repo.allReleases.nodeList
                                    .firstOrNull { it.id == id.id }
                                    ?.let {
                                        return GHRelease(
                                            GitHubId(it.id),
                                            it.release_name,
                                            it.release_type == ReleaseType.ea,
                                            it.timestamp.dateTime.toString(),
                                            it.updated_at.dateTime.toString(),
                                            GHAssets(
                                                it.binaries.map { binary ->
                                                    GHAsset(
                                                        binary.`package`.name,
                                                        binary.`package`.size,
                                                        binary.`package`.link,
                                                        binary.`package`.download_count,
                                                        binary.updated_at.dateTime.toString()
                                                    )
                                                },
                                                PageInfo(false, null),
                                                it.binaries.size
                                            ),
                                            it.id,
                                            "/AdoptOpenJDK/openjdk${it.version_data.major}-binaries/releases/tag/jdk8u-2020-01-09-03-36"
                                        )
                                    }
                            }
                        },
                        AdoptReleaseMapperFactory(
                            AdoptBinaryMapper(ghClient),
                            ghClient
                        )
                    ),
                    vs
                ),
                apiDataStore,
                InMemoryApiPersistence(repo),
                mockk(),
                ReleaseVersionResolver(vs),
                mockk(),
                vs
            )

            var updatedRepo = updater.runUpdate(repo, AtomicBoolean(true), mockk())

            assertTrue(updatedRepo == repo)
        }
    }

    @Test
    fun `checksum works`() {
        runBlocking {
            val checksum = V3Updater.calculateChecksum(BaseTest.adoptRepos)
            assertTrue(checksum.length == 44)
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "adoptopenjdktests", matches = "true")
    fun `adoptOpenJdk releases are copied over`() {

        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val filter = ReleaseIncludeFilter(
                TimeSource.now(),
                ReleaseFilterType.ALL,
                false,
                setOf(Vendor.adoptopenjdk)
            )

            val adoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { filter.filter(it.vendor, it.updated_at.dateTime, it.release_type == ReleaseType.ea) }
                .toList()
            )

            val notAdoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { !filter.filter(it.vendor, it.updated_at.dateTime, it.release_type == ReleaseType.ea) }
                .toList()
            )

            val concated = V3Updater.copyOldReleasesIntoNewRepo(adoptopenjdk, notAdoptopenjdk, filter)

            Assertions.assertEquals(repo, concated)
        }
    }
}
