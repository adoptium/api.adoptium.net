package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime

class AdoptReposBuilderSplitReleaseTest {

    @Test
    fun `updated rolling release replaces obsolete split IDs`() {
        checkRefresh(updated = true, ea = true)
    }

    @Test
    fun `explicit refresh retains every current split release`() {
        checkRefresh(explicit = true, ea = true)
    }

    @Test
    fun `binary count refresh replaces obsolete split IDs`() {
        checkRefresh(binaryCountChanged = true, ea = true)
    }

    @Test
    fun `young release refresh replaces obsolete split IDs`() {
        checkRefresh(young = true, ea = true)
    }

    @Test
    fun `overlapping selectors fetch only one complete upstream snapshot`() {
        checkRefresh(updated = true, explicit = true, binaryCountChanged = true, young = true, ea = true)
    }

    @Test
    fun `latest EA polling does not refetch a selected release mapped to no splits`() {
        checkRefresh(explicit = true, ea = true, empty = true)
    }

    @Test
    fun `successful empty selected GA refresh removes every existing split`() {
        checkRefresh(explicit = true, empty = true)
    }

    @Test
    fun `failed selected EA refresh preserves splits without refetching`() {
        checkRefresh(explicit = true, ea = true, failed = true)
    }

    @Test
    fun `failed refresh keeps existing split releases`() {
        checkRefresh(updated = true, failed = true)
    }

    @Test
    fun `unchanged GA split releases are retained without fetching`() {
        checkRefresh()
    }

    @Test
    fun `refresh preserves unrelated releases with a similar ID prefix`() {
        checkRefresh(updated = true, unrelatedId = "rolling-other")
    }

    @Test
    fun `latest old EA refreshes despite unchanged release timestamp and asset count`() {
        checkRefresh(ea = true)
    }

    @Test
    fun `successful empty latest-only EA refresh removes every existing split`() {
        checkRefresh(ea = true, empty = true)
    }

    @Test
    fun `failed latest old EA refresh preserves existing split releases`() {
        checkRefresh(ea = true, failed = true)
    }

    @Test
    fun `null fetch preserves splits and retries the next incremental update`() = runBlocking {
        val old = release("rolling.old", TimeSource.now().minusDays(20), ReleaseType.ea)
        val current = release("rolling.new", TimeSource.now().minusDays(20), ReleaseType.ea)
        val before = repo(listOf(old))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before)
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        coEvery { repository.getReleaseById(GitHubId("rolling")) } returnsMany listOf(null, ReleaseResult(listOf(current)))
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })

        val deferred = builder.incrementalUpdate(emptySet(), before) { null }
        assertEquals(before, deferred)

        val recovered = builder.incrementalUpdate(emptySet(), deferred) { null }
        assertEquals(listOf(current).associateBy { it.id }, recovered.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 2) { repository.getReleaseById(GitHubId("rolling")) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `one EA fetch exception preserves its splits while other EAs update and later retries`(explicit: Boolean) = runBlocking {
        val time = TimeSource.now().minusDays(20)
        fun ea(id: String): Release = release(id, time, ReleaseType.ea, GitHubId(id).id)
        val healthyBefore = ea("healthy-before.old")
        val failed = ea("failing.old")
        val failedSplit = ea("failing.second")
        val healthyAfter = ea("healthy-after.old")
        val before = repo(listOf(healthyBefore, failed, failedSplit, healthyAfter))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(repo(listOf(healthyBefore, failed, healthyAfter)))
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        val refreshedBefore = ea("healthy-before.new")
        val refreshedAfter = ea("healthy-after.new")
        val recovered = ea("failing.new")
        coEvery { repository.getReleaseById(GitHubId("healthy-before")) } returns ReleaseResult(listOf(refreshedBefore))
        coEvery { repository.getReleaseById(GitHubId("failing")) } throws IllegalStateException("Asset pagination failed")
        coEvery { repository.getReleaseById(GitHubId("healthy-after")) } returns ReleaseResult(listOf(refreshedAfter))
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })
        val requested = if (explicit) setOf(healthyBefore.release_name, failed.release_name, healthyAfter.release_name) else emptySet()

        val after = builder.incrementalUpdate(requested, before) { null }

        assertEquals(listOf(refreshedBefore, failed, failedSplit, refreshedAfter).associateBy { it.id },
            after.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(listOf(healthyBefore, failed, failedSplit, healthyAfter).associateBy { it.id },
            before.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 1) { repository.getReleaseById(GitHubId("healthy-before")) }
        coVerify(exactly = 1) { repository.getReleaseById(GitHubId("failing")) }
        coVerify(exactly = 1) { repository.getReleaseById(GitHubId("healthy-after")) }
        coEvery { repository.getReleaseById(GitHubId("failing")) } returns ReleaseResult(listOf(recovered))

        val retried = builder.incrementalUpdate(requested, after) { null }

        assertEquals(listOf(refreshedBefore, recovered, refreshedAfter).associateBy { it.id },
            retried.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 2) { repository.getReleaseById(GitHubId("failing")) }
    }

    @Test
    fun `release refresh cancellation propagates instead of being treated as a failed EA`() {
        val old = release("rolling.old", TimeSource.now().minusDays(20), ReleaseType.ea)
        val before = repo(listOf(old))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before)
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        val cancellation = CancellationException("Update cancelled")
        coEvery { repository.getReleaseById(GitHubId("rolling")) } throws cancellation
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { builder.incrementalUpdate(setOf(old.release_name), before) { null } }
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(old).associateBy { it.id }, before.getFeatureRelease(8)!!.releases.nodes)
    }

    @Test
    fun `pruning removes deleted releases sharing an upstream ID prefix`() = runBlocking {
        val time = TimeSource.now().minusDays(20)
        val retained = release("rolling.one", time)
        val retainedSplit = release("rolling.two", time)
        val deleted = release("rolling-long.deleted", time)
        val before = repo(listOf(retained, retainedSplit, deleted))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(repo(listOf(retained)))
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })

        val after = builder.incrementalUpdate(emptySet(), before) { null }

        assertEquals(listOf(retained, retainedSplit).associateBy { it.id }, after.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 0) { repository.getReleaseById(any()) }
    }

    @Test
    fun `upstream ID lookup and timestamp comparison use exact normalized IDs`() {
        val time = TimeSource.now().minusDays(20)
        val releases = Releases(listOf(release("rolling-long.split", time)))

        assertFalse(releases.hasReleaseId(GitHubId("rolling")))
        assertFalse(releases.hasReleaseBeenUpdated(GitHubId("rolling"), time.plusDays(1)))
        assertTrue(releases.hasReleaseId(GitHubId("rolling-long")))
        assertTrue(releases.hasReleaseBeenUpdated(GitHubId("rolling-long"), time.plusDays(1)))
        assertFalse(releases.hasReleaseBeenUpdated(GitHubId("rolling-long"), time))
    }

    private fun checkRefresh(
        updated: Boolean = false,
        explicit: Boolean = false,
        binaryCountChanged: Boolean = false,
        young: Boolean = false,
        failed: Boolean = false,
        empty: Boolean = false,
        unrelatedId: String = "unrelated",
        ea: Boolean = false
    ) = runBlocking {
        val oldTime = TimeSource.now().minusDays(if (young) 2 else 20)
        val refreshedTime = if (updated) oldTime.plusDays(1) else oldTime
        val releaseType = if (ea) ReleaseType.ea else ReleaseType.ga
        val old = release("rolling.old", oldTime, releaseType)
        val obsolete = release("rolling.obsolete", oldTime, releaseType)
        val unrelated = release("$unrelatedId.untouched", TimeSource.now().minusDays(20))
        val current = listOf(release("rolling.new", refreshedTime, releaseType), release("rolling.second", refreshedTime, releaseType))
        val before = repo(listOf(old, obsolete, unrelated))
        // GitHub has one summary per upstream release, even when the mapper splits it.
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(repo(listOf(current.first(), unrelated)))
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        // A second fetch would represent a different upstream snapshot. Never mix it
        // with the first, even when all selectors match the same upstream release.
        coEvery { repository.getReleaseById(GitHubId("rolling")) } returnsMany listOf(
            if (failed) ReleaseResult(error = "Failed to fetch") else ReleaseResult(if (empty) emptyList() else current),
            ReleaseResult(listOf(release("rolling.later", refreshedTime, releaseType)))
        )
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })

        val after = builder.incrementalUpdate(if (explicit) setOf(old.release_name) else emptySet(), before) {
            if (binaryCountChanged && it == GitHubId("rolling")) GHReleaseMetadata(100, it) else null
        }

        val shouldFetch = updated || explicit || binaryCountChanged || young || ea
        val expected = when {
            !shouldFetch || failed -> listOf(old, obsolete, unrelated)
            empty -> listOf(unrelated)
            else -> current + unrelated
        }
        assertEquals(expected.associateBy { it.id }, after.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(listOf(old, obsolete, unrelated).associateBy { it.id }, before.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = if (shouldFetch) 1 else 0) { repository.getReleaseById(GitHubId("rolling")) }
        coVerify(exactly = 0) { repository.getReleaseById(GitHubId(unrelatedId)) }
    }

    private fun repo(releases: List<Release>): AdoptRepos {
        return AdoptRepos(listOf(FeatureRelease(8, Releases(releases))))
    }

    private fun release(
        id: String,
        updatedAt: ZonedDateTime,
        releaseType: ReleaseType = ReleaseType.ga,
        repository: String = "temurin8-binaries"
    ): Release {
        return Release(
            id, releaseType, "https://github.com/adoptium/$repository/releases/tag/${GitHubId(id).id}",
            GitHubId(id).id, DateTime(TimeSource.now().minusDays(30)), DateTime(updatedAt),
            emptyArray(), 0, Vendor.eclipse,
            VersionData(8, 0, 512, "beta", 0, 5, id.substringAfter('.'), "1.8.0_512-beta-b05")
        )
    }
}
