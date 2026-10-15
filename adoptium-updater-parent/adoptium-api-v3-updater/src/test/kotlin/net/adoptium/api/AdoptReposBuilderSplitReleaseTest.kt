package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.jupiter.api.Test
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
    fun `failed latest old EA refresh preserves existing split releases`() {
        checkRefresh(ea = true, failed = true)
    }

    private fun checkRefresh(
        updated: Boolean = false,
        explicit: Boolean = false,
        binaryCountChanged: Boolean = false,
        young: Boolean = false,
        failed: Boolean = false,
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
        coEvery { repository.getReleaseById(GitHubId("rolling")) } returns
            if (failed) ReleaseResult(error = "Failed to fetch") else ReleaseResult(current)
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier>())

        val after = builder.incrementalUpdate(if (explicit) setOf(old.release_name) else emptySet(), before) {
            if (binaryCountChanged && it == GitHubId("rolling")) GHReleaseMetadata(100, it) else null
        }

        val shouldFetch = updated || explicit || binaryCountChanged || young || ea
        val expected = if (shouldFetch && !failed) current + unrelated else listOf(old, obsolete, unrelated)
        assertEquals(expected.associateBy { it.id }, after.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(listOf(old, obsolete, unrelated).associateBy { it.id }, before.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = if (shouldFetch) 1 else 0) { repository.getReleaseById(GitHubId("rolling")) }
        coVerify(exactly = 0) { repository.getReleaseById(GitHubId(unrelatedId)) }
    }

    private fun repo(releases: List<Release>): AdoptRepos {
        return AdoptRepos(listOf(FeatureRelease(8, Releases(releases))))
    }

    private fun release(id: String, updatedAt: ZonedDateTime, releaseType: ReleaseType = ReleaseType.ga): Release {
        return Release(
            id, releaseType, "https://github.com/adoptium/temurin8-binaries/releases/tag/${GitHubId(id).id}",
            GitHubId(id).id, DateTime(TimeSource.now().minusDays(30)), DateTime(updatedAt),
            emptyArray(), 0, Vendor.eclipse,
            VersionData(8, 0, 512, "beta", 0, 5, id.substringAfter('.'), "1.8.0_512-beta-b05")
        )
    }
}
