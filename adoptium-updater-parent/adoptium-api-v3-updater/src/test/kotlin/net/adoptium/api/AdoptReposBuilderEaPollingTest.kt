package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
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
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.mapping.upstream.UpstreamReleaseMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AdoptReposBuilderEaPollingTest {
    private val now = TimeSource.now().withNano(0)

    @Test
    fun `date free upstream names use mapper classification and poll old latest EA`() = runBlocking {
        val ea = release("jdk8u302-b08", now.minusDays(30), repository = "openjdk8-upstream-binaries", vendor = Vendor.openjdk)
        val ga = release("OpenJDK 8u302 GA Release", now.minusDays(20), repository = "openjdk8-upstream-binaries", vendor = Vendor.openjdk, type = ReleaseType.ga)
        val namedEa = release("OpenJDK 8u242 EA Release", now.minusDays(40), repository = "openjdk8-upstream-binaries", vendor = Vendor.openjdk)
        // The upstream mapper uses the name, even when GitHub says prerelease=false.
        for (release in listOf(ea, ga, namedEa)) {
            assertEquals(release.release_type, ReleaseMapper.releaseType(release.release_name, release.release_link, false))
        }
        val gh = GHRelease(GitHubId(ea.id), ea.release_name, false,
            summary(ea).publishedAt, summary(ea).updatedAt, GHAssets(emptyList(), PageInfo(false, null), 0), "", ea.release_link)
        assertEquals(ReleaseType.ea, UpstreamReleaseMapper.toAdoptRelease(gh).result!!.single().release_type)
        checkPolling(listOf(ea, ga, namedEa), setOf(ea.id), prerelease = false)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `unchanged completed outcomes are not new and summary changes invalidate them`(excluded: Boolean) = runBlocking {
        val old = release("old", now.minusDays(40), type = ReleaseType.ga)
        var current = summary(old)
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } answers {
            GHRepositorySummary(GHReleasesSummary(listOf(current), PageInfo(false, null)))
        }
        coEvery { repository.getReleaseById(GitHubId(old.id)) } returns
            if (excluded) ReleaseResult(error = "Excluded", retryable = false) else ReleaseResult(emptyList())
        val builder = AdoptReposBuilder(repository, mockk())
        var snapshot = repo(listOf(old))
        suspend fun update(requested: Set<String> = emptySet()) {
            // Persistence can still have old metadata after every split was removed.
            snapshot = builder.incrementalUpdate(requested, snapshot) { GHReleaseMetadata(999, it) }
            assertEquals(emptyMap<String, Release>(), snapshot.allReleases.nodes)
        }
        update(setOf(old.release_name))
        repeat(3) { update() }
        coVerify(exactly = 1) { repository.getReleaseById(any()) }
        val changes: List<(GHReleaseSummary) -> GHReleaseSummary> = listOf(
            { it.copy(updatedAt = DateTimeFormatter.ISO_INSTANT.format(now.minusDays(20))) },
            { it.copy(publishedAt = DateTimeFormatter.ISO_INSTANT.format(now.minusDays(30))) },
            { it.copy(name = "renamed") },
            { it.copy(releaseAssets = GHAssetsSummary(2)) },
            { it.copy(url = it.url!!.replace("temurin8", "openjdk8")) },
            { it.copy(vendor = Vendor.adoptopenjdk) },
            { it.copy(isPrerelease = null) }
        )
        changes.forEachIndexed { index, change ->
            current = change(current)
            update()
            update()
            coVerify(exactly = index + 2) { repository.getReleaseById(any()) }
        }
        // An explicit failed retry must clear the remembered completion and retry next cycle.
        coEvery { repository.getReleaseById(any()) } returns ReleaseResult(error = "Transient")
        update(setOf(current.name))
        update()
        coVerify(exactly = 10) { repository.getReleaseById(any()) }
        coEvery { repository.getReleaseById(any()) } returns ReleaseResult(listOf(old))
        snapshot = builder.incrementalUpdate(emptySet(), snapshot) { null }
        assertEquals(repo(listOf(old)), snapshot)
    }

    @ParameterizedTest
    @ValueSource(strings = ["latest-ea", "recent-ea", "recent-ga", "edited-ga"])
    fun `empty snapshots retain periodic refreshes but permanent exclusions do not`(trigger: String) = runBlocking {
        val old = release("empty", if (trigger.startsWith("recent")) now.minusDays(2) else now.minusDays(30),
            type = if (trigger.endsWith("ga")) ReleaseType.ga else ReleaseType.ea,
            updated = if (trigger == "edited-ga") now.minusDays(1) else now.minusDays(20))
        val latest = release("latest", now.minusDays(10))
        for (excluded in listOf(false, true)) {
            val repository = mockk<AdoptRepository>()
            coEvery { repository.getSummary(8) } returns GHRepositorySummary(GHReleasesSummary(
                (if (trigger == "recent-ea") listOf(old, latest) else listOf(old)).map(::summary), PageInfo(false, null)))
            coEvery { repository.getReleaseById(any()) } returns
                if (excluded) ReleaseResult(error = "Excluded", retryable = false) else ReleaseResult(emptyList())
            val builder = AdoptReposBuilder(repository, mockk())
            var snapshot = repo(listOf(old))
            repeat(3) { snapshot = builder.incrementalUpdate(emptySet(), snapshot) { null } }
            coVerify(exactly = if (excluded) 1 else 3) { repository.getReleaseById(GitHubId(old.id)) }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `completed outcomes are forgotten when an upstream ID disappears`(excluded: Boolean) = runBlocking {
        val old = release("old", now.minusDays(30), type = ReleaseType.ga)
        var summaries = listOf(summary(old))
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } answers { GHRepositorySummary(GHReleasesSummary(summaries, PageInfo(false, null))) }
        coEvery { repository.getReleaseById(any()) } returns
            if (excluded) ReleaseResult(error = "Excluded", retryable = false) else ReleaseResult(emptyList())
        val builder = AdoptReposBuilder(repository, mockk())
        var snapshot = repo(listOf(old))
        snapshot = builder.incrementalUpdate(setOf(old.release_name), snapshot) { null }
        summaries = emptyList()
        snapshot = builder.incrementalUpdate(emptySet(), snapshot) { null }
        summaries = listOf(summary(old))
        builder.incrementalUpdate(emptySet(), snapshot) { null }
        coVerify(exactly = 2) { repository.getReleaseById(any()) }
        builder.incrementalUpdate(emptySet(), AdoptRepos(emptyList())) { null }
        builder.incrementalUpdate(emptySet(), snapshot) { null }
        coVerify(exactly = 3) { repository.getReleaseById(any()) }
    }

    @Test
    fun `a thousand edited historical EAs only fetch the latest published EA on each cycle`() {
        val history = (0 until 1000).map {
            release("history$it", now.minusDays(40).minusMinutes(it.toLong()), updated = now.minusHours(1))
        }
        // Deliberately not ordered by publication, update time, ID, or API version.
        checkPolling(listOf(release("latest", now.minusDays(20))) + history.reversed(), setOf("latest"))
    }

    @Test
    fun `latest is independent per vendor upstream repo and Java feature version`() {
        val releases = listOf(
            release("a-old", now.minusDays(50)), release("a-new", now.minusDays(30)),
            release("b-old", now.minusDays(50), repository = "openjdk8-binaries"),
            release("b-new", now.minusDays(35), repository = "openjdk8-binaries"),
            release("ibm-old", now.minusDays(50), vendor = Vendor.ibm),
            release("ibm-new", now.minusDays(40), vendor = Vendor.ibm),
            release("11-old", now.minusDays(50), major = 11),
            release("11-new", now.minusDays(45), major = 11)
        )
        checkPolling(releases, setOf("a-new", "b-new", "ibm-new", "11-new"))
    }

    @Test
    fun `recent publication window excludes cutoff but newer GA does not suppress latest EA`() {
        val releases = listOf(release("newest", now.minusDays(1)), release("recent", now.minusDays(6)),
            release("cutoff", now.minusDays(7)), release("older", now.minusDays(8)),
            release("ga", now.minusHours(12), type = ReleaseType.ga))
        checkPolling(releases, setOf("newest", "recent", "ga"))
    }

    @Test
    fun `new latest is selected before fetching without polling previous latest`() {
        val previous = release("previous", now.minusDays(30))
        checkPolling(listOf(previous), setOf("new"), current = listOf(previous, release("new", now.minusDays(20))))
    }

    @Test
    fun `deleting latest makes previous EA the polling target`() {
        val previous = release("previous", now.minusDays(30))
        checkPolling(listOf(previous, release("deleted", now.minusDays(20))), setOf("previous"), current = listOf(previous))
    }

    @Test
    fun `explicit count and update triggers still refresh old EAs`() {
        val releases = listOf(release("explicit", now.minusDays(50)), release("count", now.minusDays(40)),
            release("edited", now.minusDays(30)), release("latest", now.minusDays(20)))
        checkPolling(releases, setOf("explicit", "count", "edited", "latest"),
            current = releases.map { if (it.id == "edited") release("edited", now.minusDays(30), updated = now.minusHours(1)) else it },
            requested = setOf("explicit"), countChanged = "count", repeatCycle = false)
    }

    @Test
    fun `recently edited GA retains existing refresh rule but old edited EAs do not`() {
        checkPolling(listOf(release("old", now.minusDays(40), updated = now.minusDays(1)),
            release("latest", now.minusDays(30)), release("ga", now.minusDays(40), type = ReleaseType.ga, updated = now.minusDays(1))),
            setOf("latest", "ga"))
    }

    @Test
    fun `latest EA and explicit requests respect upload grace period`() {
        checkPolling(listOf(release("old", now.minusDays(30)), release("uploading", now.minusMinutes(5))),
            emptySet(), requested = setOf("uploading"), countChanged = "uploading")
    }

    @Test
    fun `overlapping periodic triggers fetch once and preserve failure or successful empty snapshots`() = runBlocking {
        val old = release("rolling.old-split", now.minusDays(2))
        val other = release("rolling.other-split", now.minusDays(2))
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns GHRepositorySummary(GHReleasesSummary(
            listOf(summary(old), summary(other)), PageInfo(false, null)))
        val builder = AdoptReposBuilder(repository, mockk())
        val before = repo(listOf(old, other))
        coEvery { repository.getReleaseById(GitHubId("rolling")) } returns ReleaseResult(error = "Retry")
        assertEquals(before, builder.incrementalUpdate(setOf(old.release_name), before) { GHReleaseMetadata(999, it) })
        coEvery { repository.getReleaseById(GitHubId("rolling")) } returns ReleaseResult(emptyList())
        assertEquals(repo(emptyList()).allReleases.nodes, builder.incrementalUpdate(setOf(old.release_name), before) { null }.allReleases.nodes)
        coVerify(exactly = 2) { repository.getReleaseById(GitHubId("rolling")) }
    }

    private fun checkPolling(
        releases: List<Release>, expected: Set<String>, current: List<Release> = releases,
        requested: Set<String> = emptySet(), countChanged: String? = null, repeatCycle: Boolean = true,
        prerelease: Boolean? = null
    ) = runBlocking {
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(any()) } answers {
            GHRepositorySummary(GHReleasesSummary(current.filter { it.version_data.major == firstArg<Int>() }
                .map { summary(it).let { summary -> if (prerelease == null) summary else summary.copy(isPrerelease = prerelease) } }, PageInfo(false, null)))
        }
        coEvery { repository.getReleaseById(any()) } answers {
            ReleaseResult(current.filter { GitHubId(it.id) == firstArg<GitHubId>() })
        }
        val builder = AdoptReposBuilder(repository, mockk())
        val after = builder.incrementalUpdate(requested, repo(releases)) {
            if (it.id == countChanged) GHReleaseMetadata(999, it) else null
        }
        assertEquals(repo(current), after)
        if (repeatCycle) assertEquals(after, builder.incrementalUpdate(requested, after) {
            if (it.id == countChanged) GHReleaseMetadata(999, it) else null
        })
        val cycles = if (repeatCycle) 2 else 1
        coVerify(exactly = expected.size * cycles) { repository.getReleaseById(any()) }
        expected.forEach { id -> coVerify(exactly = cycles) { repository.getReleaseById(GitHubId(id)) } }
    }

    private fun summary(release: Release) = GHReleaseSummary(GitHubId(release.id),
        DateTimeFormatter.ISO_INSTANT.format(release.timestamp.dateTime), DateTimeFormatter.ISO_INSTANT.format(release.updated_at.dateTime),
        release.release_name, GHAssetsSummary(0), release.release_link, release.release_type == ReleaseType.ea, release.vendor)

    private fun repo(releases: List<Release>) = AdoptRepos(releases.groupBy { it.version_data.major }
        .map { (major, nodes) -> FeatureRelease(major, Releases(nodes)) })

    private fun release(
        id: String, published: ZonedDateTime, repository: String = "temurin8-binaries", major: Int = 8,
        vendor: Vendor = Vendor.eclipse, type: ReleaseType = ReleaseType.ea, updated: ZonedDateTime = published
    ) = Release(id, type, "https://github.com/adoptium/$repository/releases/tag/$id", id,
        DateTime(published), DateTime(updated), emptyArray(), 0, vendor,
        VersionData(major, 0, 512, "beta", 0, 5, "", ""))
}
