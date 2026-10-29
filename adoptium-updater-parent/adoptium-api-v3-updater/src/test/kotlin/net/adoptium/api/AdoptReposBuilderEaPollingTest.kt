package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime

class AdoptReposBuilderEaPollingTest {
    private val now = TimeSource.now().withNano(0)

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `incremental discovery recovers an EA-only version omitted by a full build`(keepHealthyVersion: Boolean) = runBlocking {
        val ea = release("java27-ea", now.minusDays(1), major = 27)
        val healthy = if (keepHealthyVersion) {
            listOf(release("java8-ga", now.minusDays(30), releaseType = ReleaseType.ga))
        } else emptyList()
        val before = repo(healthy + ea)
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getRelease(any(), any()) } answers {
            val major = firstArg<Int>()
            FeatureRelease(major, Releases(healthy.filter { it.version_data.major == major }))
        }
        coEvery { repository.getSummary(any()) } answers {
            val major = firstArg<Int>()
            GHSummaryTestDataGenerator.generateGHRepositorySummary(
                GHSummaryTestDataGenerator.generateGHRepository(repo((healthy + ea).filter { it.version_data.major == major }))
            )
        }
        coEvery { repository.getReleaseById(GitHubId(ea.id)) } returnsMany listOf(
            ReleaseResult(error = "Metadata unavailable", retryable = true),
            ReleaseResult(listOf(ea))
        )
        val versions = mockk<VersionSupplier>()
        every { versions.getAllVersions() } returns before.repos.keys.toTypedArray()
        val builder = AdoptReposBuilder(repository, versions)
        val rebuilt = builder.build(ReleaseIncludeFilter.INCLUDE_ALL)
        val omitted = V3Updater.copyOldReleasesIntoNewRepo(before, rebuilt, ReleaseIncludeFilter.INCLUDE_ALL)
        assertEquals(repo(healthy), omitted)

        every { versions.getAllVersions() } returns arrayOf(27, 27)
        val deferred = builder.incrementalUpdate(emptySet(), omitted) { null }
        assertEquals(healthy.associateBy { it.id }, deferred.allReleases.nodes)
        val recovered = builder.incrementalUpdate(emptySet(), deferred) { null }

        assertEquals(repo(healthy + ea), recovered)
        coVerify(exactly = 2) { repository.getSummary(27) }
        coVerify(exactly = if (keepHealthyVersion) 2 else 0) { repository.getSummary(8) }
        coVerify(exactly = 2) { repository.getReleaseById(any()) }
    }

    @Test
    fun `incremental failure that began before a full build cannot re-exclude the corrected release`() = runBlocking {
        val ea = release("fixed-ea", now.minusDays(1))
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getRelease(any(), any()) } returns FeatureRelease(8, Releases(emptyList()))
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(repo(listOf(ea)))
        )
        coEvery { repository.getReleaseById(GitHubId(ea.id)) } coAnswers {
            if (calls++ == 0) {
                entered.complete(Unit)
                gate.await()
                ReleaseResult(error = "Malformed", retryable = false)
            } else {
                ReleaseResult(listOf(ea))
            }
        }
        val versions = mockk<VersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        val builder = AdoptReposBuilder(repository, versions)

        val stale = async { builder.incrementalUpdate(emptySet(), AdoptRepos(emptyList())) { null } }
        withTimeout(5000) { entered.await() }
        builder.build(ReleaseIncludeFilter.INCLUDE_ALL)
        gate.complete(Unit)
        stale.await()

        val recovered = builder.incrementalUpdate(emptySet(), AdoptRepos(emptyList())) { null }
        assertEquals(repo(listOf(ea)), recovered)
        coVerify(exactly = 2) { repository.getReleaseById(any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `failure from a refresh started during a successful full build cannot re-exclude its result`(failureFinishesAfterBuild: Boolean) = runBlocking {
        withTimeout(5000) {
            val ea = release("fixed-ea", now.minusDays(1))
            val before = repo(listOf(ea))
            val fullStarted = CompletableDeferred<Unit>()
            val finishFull = CompletableDeferred<Unit>()
            val refreshStarted = CompletableDeferred<Unit>()
            val finishRefresh = CompletableDeferred<Unit>()
            val repository = mockk<AdoptRepository>()
            coEvery { repository.getRelease(8, any()) } coAnswers {
                fullStarted.complete(Unit)
                finishFull.await()
                FeatureRelease(8, Releases(listOf(ea)))
            }
            coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
                GHSummaryTestDataGenerator.generateGHRepository(before)
            )
            coEvery { repository.getReleaseById(GitHubId(ea.id)) } coAnswers {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                ReleaseResult(error = "Malformed", retryable = false)
            }
            val versions = mockk<VersionSupplier>()
            every { versions.getAllVersions() } returns arrayOf(8)
            val builder = AdoptReposBuilder(repository, versions)

            val full = async { builder.build(ReleaseIncludeFilter.INCLUDE_ALL) }
            fullStarted.await()
            val stale = async { builder.incrementalUpdate(emptySet(), before) { null } }
            refreshStarted.await()
            if (!failureFinishesAfterBuild) {
                finishRefresh.complete(Unit)
                stale.await()
            }
            finishFull.complete(Unit)
            val rebuilt = full.await()
            assertEquals(before, rebuilt)
            if (failureFinishesAfterBuild) {
                finishRefresh.complete(Unit)
                stale.await()
            }

            val refreshed = release(ea.id, now.minusDays(1), updatedAt = now.minusHours(1))
            coEvery { repository.getReleaseById(GitHubId(ea.id)) } returns ReleaseResult(listOf(refreshed))
            val after = builder.incrementalUpdate(emptySet(), rebuilt) { null }

            assertEquals(repo(listOf(refreshed)), after)
            coVerify(exactly = 2) { repository.getReleaseById(any()) }
        }
    }

    @Test
    fun `non-retryable failures after a completed full build remain excluded`() = runBlocking {
        val ea = release("malformed-ea", now.minusDays(1))
        val before = repo(listOf(ea))
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getRelease(8, any()) } returns FeatureRelease(8, Releases(listOf(ea)))
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before)
        )
        coEvery { repository.getReleaseById(GitHubId(ea.id)) } returns ReleaseResult(error = "Malformed", retryable = false)
        val versions = mockk<VersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        val builder = AdoptReposBuilder(repository, versions)
        val rebuilt = builder.build(ReleaseIncludeFilter.INCLUDE_ALL)

        repeat(2) {
            assertEquals(rebuilt, builder.incrementalUpdate(emptySet(), rebuilt) { null })
        }

        coVerify(exactly = 1) { repository.getReleaseById(any()) }
    }

    @Test
    fun `a thousand historical EAs only fetch the latest old EA`() {
        val history = (0 until 1000).map {
            release("history${it.toString().padStart(4, '0')}", now.minusDays(40).minusMinutes(it.toLong()))
        }
        val latest = release("latest", now.minusDays(20))

        checkPolling(history + latest, setOf("latest"))
    }

    @Test
    fun `all EAs published in the last week are fetched but older history is skipped`() {
        val releases = listOf(
            release("newest", now.minusDays(1)),
            release("recent", now.minusDays(6)),
            release("cutoff", now.minusDays(7)),
            release("older", now.minusDays(8))
        )

        checkPolling(releases, setOf("newest", "recent"))
    }

    @Test
    fun `latest is selected separately for repositories with the same vendor`() {
        val releases = listOf(
            release("repo-a-old", now.minusDays(40), repository = "repo-a"),
            release("repo-a-new", now.minusDays(20), repository = "repo-a"),
            release("repo-b-old", now.minusDays(50), repository = "repo-b"),
            release("repo-b-new", now.minusDays(30), repository = "repo-b")
        )

        checkPolling(releases, setOf("repo-a-new", "repo-b-new"))
    }

    @Test
    fun `latest is selected separately for Java feature versions`() {
        val releases = listOf(
            release("java8-old", now.minusDays(40)),
            release("java8-new", now.minusDays(20)),
            release("java11-old", now.minusDays(50), major = 11),
            release("java11-new", now.minusDays(30), major = 11)
        )

        checkPolling(releases, setOf("java8-new", "java11-new"))
    }

    @Test
    fun `newly discovered latest EA avoids refreshing the previous latest`() {
        val previous = release("previous", now.minusDays(30))
        val latest = release("latest", now.minusDays(20))

        checkPolling(listOf(previous), setOf("latest"), current = listOf(previous, latest))
    }

    @Test
    fun `a newer GA release does not suppress the latest EA`() {
        val releases = listOf(
            release("ea-older", now.minusDays(40)),
            release("ea-latest", now.minusDays(20)),
            release("ga-newer", now.minusDays(10), releaseType = ReleaseType.ga)
        )

        checkPolling(releases, setOf("ea-latest"))
    }

    @Test
    fun `older EAs can still be refreshed explicitly`() {
        val releases = listOf(release("older", now.minusDays(40)), release("latest", now.minusDays(20)))

        checkPolling(releases, setOf("older", "latest"), requested = setOf("older"))
    }

    @Test
    fun `older EAs still refresh when their asset count changes`() {
        val releases = listOf(release("older", now.minusDays(40)), release("latest", now.minusDays(20)))

        checkPolling(releases, setOf("older", "latest"), countChanged = "older")
    }

    @Test
    fun `removing the latest EA makes the previous EA the polling target`() {
        val previous = release("previous", now.minusDays(30))
        val deleted = release("deleted", now.minusDays(20))

        checkPolling(listOf(previous, deleted), setOf("previous"), current = listOf(previous))
    }

    @Test
    fun `recently edited EA history is not routinely repolled after its update is recorded`() {
        val releases = listOf(
            release("older", now.minusDays(40), updatedAt = now.minusDays(1)),
            release("middle", now.minusDays(30), updatedAt = now.minusDays(1)),
            release("latest", now.minusDays(20), updatedAt = now.minusDays(1))
        )

        checkPolling(releases, setOf("latest"))
    }

    @Test
    fun `recently edited GA releases retain the existing refresh rule`() {
        val releases = listOf(
            release("ga", now.minusDays(40), releaseType = ReleaseType.ga, updatedAt = now.minusDays(1)),
            release("latest", now.minusDays(20))
        )

        checkPolling(releases, setOf("ga", "latest"))
    }

    @Test
    fun `latest EA still respects the ten minute upload grace period`() {
        val releases = listOf(release("older", now.minusDays(20)), release("uploading", now.minusMinutes(5)))

        checkPolling(releases, emptySet())
    }

    private fun checkPolling(
        releases: List<Release>,
        expected: Set<String>,
        current: List<Release> = releases,
        requested: Set<String> = emptySet(),
        countChanged: String? = null
    ) = runBlocking {
        val before = repo(releases)
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(any()) } coAnswers {
            val major = firstArg<Int>()
            GHSummaryTestDataGenerator.generateGHRepositorySummary(
                GHSummaryTestDataGenerator.generateGHRepository(repo(current.filter { it.version_data.major == major }))
            )
        }
        coEvery { repository.getReleaseById(any()) } coAnswers {
            val id = firstArg<GitHubId>()
            ReleaseResult(current.filter { GitHubId(it.id) == id })
        }
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns before.repos.keys.toTypedArray()
        })

        val after = builder.incrementalUpdate(requested, before) {
            if (it.id == countChanged) GHReleaseMetadata(999, it) else null
        }

        assertEquals(repo(current), after)
        coVerify(exactly = expected.size) { repository.getReleaseById(any()) }
        expected.forEach { id ->
            coVerify(exactly = 1) { repository.getReleaseById(GitHubId(id)) }
        }
    }

    private fun repo(releases: List<Release>): AdoptRepos {
        return AdoptRepos(releases.groupBy { it.version_data.major }.map { (major, nodes) ->
            FeatureRelease(major, Releases(nodes))
        })
    }

    private fun release(
        id: String,
        publishedAt: ZonedDateTime,
        repository: String = "temurin8-binaries",
        major: Int = 8,
        releaseType: ReleaseType = ReleaseType.ea,
        updatedAt: ZonedDateTime = publishedAt
    ): Release {
        return Release(
            id, releaseType, "https://github.com/adoptium/$repository/releases/tag/$id", id,
            DateTime(publishedAt), DateTime(updatedAt), emptyArray(), 0, Vendor.eclipse,
            VersionData(major, 0, 512, "beta", 0, 5, "", "")
        )
    }
}
