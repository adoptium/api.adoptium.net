package net.adoptium.api

import com.mongodb.MongoException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptCdxaReposBuilder
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.stats.StatsInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseFailurePolicyTest {
    private val before = BaseTest.adoptRepos
    private val builder = mockk<AdoptReposBuilder>()
    private val stats = mockk<StatsInterface>(relaxed = true)

    private fun updater(database: InMemoryApiPersistence): V3Updater {
        val versions = mockk<UpdatableVersionSupplier>()
        coEvery { versions.updateVersions() } returns Unit
        coEvery { builder.build(any()) } returns before
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } answers { secondArg() }
        return V3Updater(builder, mockk(), APIDataStoreImpl(database), database, stats,
            mockk(relaxed = true), mockk(relaxed = true), versions)
    }

    @Test
    fun `database read failure abandons the cycle without an immediate retry`() = runBlocking {
        val database = spyk(InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())))
        val updater = updater(database)
        val failure = MongoException("Snapshot read failed")
         coEvery { database.readAllReleaseData() } throws failure

        assertSame(failure, assertThrows(MongoException::class.java) {
            updater.runUpdate(before, AtomicBoolean(true), mockk())
        })
        coVerify(exactly = 1) { database.readAllReleaseData() }
        coVerify(exactly = 0) {
            builder.build(any())
            builder.incrementalUpdate(any(), any(), any(), any())
            database.updateAllRepos(any(), any())
        }

        coEvery { database.readAllReleaseData() } returns before
        assertEquals(before, updater.runUpdate(before, AtomicBoolean(true), mockk()))
        coVerify(exactly = 2) { builder.build(any()) }
    }

    @Test
    fun `failed publication leaves the previous catalogue usable until a later cycle publishes`() = runBlocking {
        val failure = MongoException("Publication failed")
        val database = object : InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())) {
            var fail = true
            override suspend fun updateAllRepos(repos: AdoptRepos, checksum: String) {
                if (fail) throw failure
                super.updateAllRepos(repos, checksum)
            }
        }
        val updater = updater(database)

        assertSame(failure, assertThrows(MongoException::class.java) {
            updater.runUpdate(before, AtomicBoolean(true), mockk())
        })
        coVerify(exactly = 1) { builder.build(any()) }
        coVerify(exactly = 0) {
            builder.incrementalUpdate(any(), any(), any(), any())
            stats.update(any())
        }
        val published = database.getUpdatedAt()
        assertEquals(before, APIDataStoreImpl.loadUpdaterSnapshot(database))
        assertEquals(before, APIDataStoreImpl(database).loadDataFromDb(true))

        database.fail = false
        assertEquals(before, updater.runUpdate(before, AtomicBoolean(true), mockk()))
        assertNotEquals(published, database.getUpdatedAt())
    }

    @ParameterizedTest
    @ValueSource(strings = ["stats", "release-info"])
    fun `database failure after publication stops remaining work without rolling back`(source: String) = runBlocking {
        val database = spyk(InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())))
        val updater = updater(database)
        val failure = MongoException("Post-publication database failure")
        if (source == "stats") coEvery { stats.update(any()) } throws failure
        else coEvery { database.setReleaseInfo(any()) } throws failure

        assertSame(failure, assertThrows(MongoException::class.java) {
            updater.runUpdate(before, AtomicBoolean(true), mockk())
        })
        coVerify(exactly = 1) {
            builder.build(any())
            database.updateAllRepos(any(), any())
        }
        coVerify(exactly = 0) { builder.incrementalUpdate(any(), any(), any(), any()) }

        coEvery { stats.update(any()) } returns Unit
        coEvery { database.setReleaseInfo(any()) } returns Unit
        assertEquals(before, updater.runUpdate(before, AtomicBoolean(true), mockk()))
    }

    @Test
    fun `scheduled incremental failure does not cancel later scheduled attempts`() {
        val database = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        val updater = updater(database)
        val executor = mockk<ScheduledExecutorService>()
        val scheduled = slot<Runnable>()
        every { executor.scheduleWithFixedDelay(capture(scheduled), any(), any(), any()) } returns mockk()
        updater.runUpdate(before, AtomicBoolean(false), executor)

        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } throws MongoException("Database unavailable")
        scheduled.captured.run()
        coEvery { builder.incrementalUpdate(any(), any(), any(), any()) } returns before
        scheduled.captured.run()

        coVerify(exactly = 4) { builder.incrementalUpdate(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed release refresh does not freeze other dataset refreshes`() {
        val published = ReleaseInfo(arrayOf(8), arrayOf(8), 8, 8, 8, 77)
        val database = spyk(InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())))
        runBlocking {
            database.setReleaseInfo(published)
        }
        val store = APIDataStoreImpl(database)
        coEvery { database.getUpdatedAt() } throws IllegalStateException("Release refresh failed")
        val periodic = APIDataStoreImpl::class.java.getDeclaredMethod("periodicUpdate").apply { isAccessible = true }

        periodic.invoke(store)

        assertEquals(77, store.getReleaseInfo().tip_version)
    }

    @Test
    fun `first-cycle database failure still registers the incremental task`() {
        val database = spyk(InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList())))
        val updater = updater(database)
        val executor = mockk<ScheduledExecutorService>()
        every { executor.scheduleWithFixedDelay(any(), any(), any(), any()) } returns mockk()
        coEvery { database.readAllReleaseData() } throws MongoException("Database unavailable")

        assertThrows(MongoException::class.java) { updater.runUpdate(before, AtomicBoolean(false), executor) }

        verify(exactly = 1) { executor.scheduleWithFixedDelay(any(), any(), any(), any()) }
    }

    @Test
    fun `first-cycle attestation database failure still registers the attestation task`() {
        val database = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        val versions = mockk<UpdatableVersionSupplier>()
        coEvery { versions.updateVersions() } returns Unit
        val cdxaBuilder = mockk<AdoptCdxaReposBuilder>()
        val failure = MongoException("Database unavailable")
        coEvery { cdxaBuilder.build() } throws failure
        val updater = V3Updater(builder, cdxaBuilder, APIDataStoreImpl(database), database, stats,
            mockk(relaxed = true), mockk(relaxed = true), versions)
        val executor = mockk<ScheduledExecutorService>()
        every { executor.scheduleWithFixedDelay(any(), any(), any(), any()) } returns mockk()
        val scheduled = AtomicBoolean(false)

        assertSame(failure, assertThrows(MongoException::class.java) {
            updater.runCdxaUpdate(AdoptCdxaRepos(emptyList()), scheduled, executor)
        })
        coVerify(exactly = 0) { cdxaBuilder.incrementalUpdate(any(), any()) }
        verify(exactly = 1) { executor.scheduleWithFixedDelay(any(), any(), any(), any()) }

        assertThrows(MongoException::class.java) {
            updater.runCdxaUpdate(AdoptCdxaRepos(emptyList()), scheduled, executor)
        }
        verify(exactly = 1) { executor.scheduleWithFixedDelay(any(), any(), any(), any()) }
    }

    @Test
    fun `incremental database failure is not isolated or excluded from the next cycle`() = runBlocking {
        val release = before.getFeatureRelease(8)!!.releases.nodeList.first()
        val snapshot = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        val repository = mockk<AdoptRepository>()
        val versions = mockk<UpdatableVersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(snapshot))
        val failure = MongoException("Incremental metadata cache unavailable")
        val id = GitHubId(release.id)
        coEvery { repository.getReleaseById(id) } throws failure
        val incremental = AdoptReposBuilder(repository, versions)
        val empty = AdoptRepos(emptyList())

        assertSame(failure, assertThrows(MongoException::class.java) {
            runBlocking { incremental.incrementalUpdate(emptySet(), empty) { null } }
        })
        coVerify(exactly = 1) { repository.getReleaseById(id) }
        coEvery { repository.getReleaseById(id) } returns ReleaseResult(result = listOf(release))
        assertEquals(snapshot, incremental.incrementalUpdate(emptySet(), empty) { null })
        coVerify(exactly = 2) { repository.getReleaseById(id) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["metadata", "checksum", "installer"])
    fun `database failure is not downgraded to an EA or optional-artifact failure`(source: String) = runBlocking {
        val failure = MongoException("Sidecar cache unavailable")
        val html = mockk<GitHubHtmlClient>()
        coEvery { html.getAsset(any()) } throws failure
        val now = TimeSource.now().minusDays(1).toInstant().toString()
        val binary = GHAsset("OpenJDK8U-jre_x64_windows_hotspot_8u242b02.zip", 1, "binary", 1, now,
            digest = "sha256:${"a".repeat(64)}")
        val sidecar = when (source) {
            "metadata" -> binary.copy(name = "${binary.name}.json", downloadUrl = "metadata", digest = null)
            "checksum" -> binary.copy(name = "${binary.name}.sha256.txt", downloadUrl = "checksum", digest = null)
            else -> binary.copy(name = binary.name.removeSuffix(".zip") + ".msi", downloadUrl = "installer", digest = null)
        }
        val assets = if (source == "installer") listOf(binary, sidecar,
            sidecar.copy(name = "${sidecar.name}.sha256.txt", downloadUrl = "installer-checksum")) else listOf(binary, sidecar)
        val repoName = if (Vendor.getDefault() == Vendor.eclipse) "temurin8-binaries" else "openjdk8-binaries"
        val release = GHRelease(GitHubId("ea"), "jdk8u242-b02", true, now, now,
            GHAssets(assets, PageInfo(false, null), assets.size), "8",
            "https://github.com/adoptium/$repoName/releases/tag/ea")
        val factory = AdoptReleaseMapperFactory(AdoptBinaryMapper(html), html)

        assertSame(failure, assertThrows(MongoException::class.java) {
            runBlocking { factory.get(Vendor.getDefault()).toAdoptRelease(release) }
        })
        val github = mockk<GitHubApi>()
        coEvery { github.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(if (secondArg<String>() == repoName) listOf(release) else emptyList(),
                PageInfo(false, null)))
        }
        assertSame(failure, assertThrows(MongoException::class.java) {
            runBlocking { AdoptRepositoryImpl(github, factory).getRelease(8, ReleaseIncludeFilter.INCLUDE_ALL) }
        })
    }
}
