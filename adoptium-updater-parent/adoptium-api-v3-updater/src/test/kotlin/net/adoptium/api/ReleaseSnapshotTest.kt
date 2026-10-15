package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
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
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** No asset identity, digest cache, retention, or bounded-EA-selection dependencies. */
class ReleaseSnapshotTest {
    private val timestamp = DateTimeFormatter.ISO_INSTANT.format(TimeSource.now().minusDays(2))
    private val first = "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz"
    private val second = "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz"
    private val sbom = "OpenJDK8U-sbom_aarch64_linux_hotspot-2.json"

    private fun asset(name: String) = GHAsset(name, 1, name, 1, timestamp)
    private fun upstream(names: List<String> = listOf(first, "$first.json", second, "$second.json")) = GHRelease(
        GitHubId("upstream"), "jdk8u242-b02", false, timestamp, timestamp,
        GHAssets(names.map(::asset), PageInfo(false, ""), names.size), "8",
        "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u242-b02"
    )

    private fun metadata(build: Int = 1, type: String = "jre") = """
        {"os":"linux","arch":"x64","variant":"hotspot","binary_type":"$type",
         "version":{"major":8,"minor":0,"security":242,"build":$build,
         "adopt_build_number":1,"version":"8.0.242+$build","semver":"8.0.242+$build.1"},
         "scmRef":"test","version_data":"jdk8u","sha256":"checksum"}
    """.trimIndent()

    private fun client(): GitHubHtmlClient = mockk<GitHubHtmlClient>().also {
        coEvery { it.getUrl(any()) } answers { metadata(if (firstArg<String>().contains("-2")) 2 else 1) }
    }

    private fun factory(client: GitHubHtmlClient) = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client)
    private fun repos(releases: List<Release>) = AdoptRepos(listOf(FeatureRelease(8, Releases(releases))))
    private fun nodes(repo: AdoptRepos) = repo.getFeatureRelease(8)!!.releases.nodes
    private fun repository(upstream: GHRelease): AdoptRepository = mockk<AdoptRepository>().also {
        // Duplicate summaries also exercise ID deduplication before fetching.
        coEvery { it.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHRepository(GHReleases(listOf(upstream, upstream), PageInfo(false, "")))
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "invalid", "exception", "missing-node", "missing-sbom-node"])
    fun `incomplete snapshot preserves all splits and recovers on the same builder`(failure: String) = runBlocking {
        val secondAsset = if (failure == "missing-sbom-node") sbom else second
        val sidecar = if (secondAsset == sbom) sbom.removeSuffix(".json") + "-metadata.json" else "$second.json"
        val complete = upstream(listOf(first, "$first.json", secondAsset, sidecar))
        val client = client()
        val mapper = factory(client).get(Vendor.eclipse)
        val previous = mapper.toAdoptRelease(complete).result!!
        assertEquals(2, previous.size)
        assertEquals(setOf(first, secondAsset), previous.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet())
        val before = repos(previous)
        val repository = repository(complete)
        var incomplete = complete
        if (failure.endsWith("node")) incomplete = upstream(listOf(first, "$first.json", secondAsset))
        else coEvery { client.getUrl(sidecar) } answers {
            when (failure) {
                "missing" -> null
                "invalid" -> "not JSON"
                else -> error("Temporary network failure")
            }
        }
        val failed = mapper.toAdoptRelease(incomplete)
        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        coEvery { repository.getReleaseById(complete.id) } returns failed
        val builder = AdoptReposBuilder(repository, mockk())
        val after = builder.incrementalUpdate(setOf(complete.name), before) { GHReleaseMetadata(999, it) }
        assertEquals(nodes(before), nodes(after))
        coVerify(exactly = 1) { repository.getReleaseById(complete.id) }

        coEvery { client.getUrl(sidecar) } returns metadata(2)
        val recovered = mapper.toAdoptRelease(complete)
        assertTrue(recovered.succeeded())
        coEvery { repository.getReleaseById(complete.id) } returns recovered
        val retried = builder.incrementalUpdate(setOf(complete.name), after) { null }
        assertEquals(recovered.result!!.associateBy { it.id }, nodes(retried))
        coVerify(exactly = 2) { repository.getReleaseById(complete.id) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "invalid", "exception"])
    fun `excluded sidecars are never fetched and sources are not binaries`(failure: String) = runBlocking {
        val source = "OpenJDK8U-sources-1.tar.gz"
        val excluded = listOf("corretto-1.tar.gz", "AQAvitTapFiles-1.tar.gz", "archive.tar.xz", "installer.msi")
        val client = client()
        excluded.forEach { name ->
            coEvery { client.getUrl("$name.json") } answers {
                when (failure) {
                    "missing" -> null
                    "invalid" -> "not JSON"
                    else -> error("Excluded sidecar must not be fetched")
                }
            }
        }
        coEvery { client.getUrl("$source.json") } returns metadata(type = "sources")
        val result = factory(client).get(Vendor.eclipse).toAdoptRelease(upstream(
            listOf(first, "$first.json", source, "$source.json") + excluded.flatMap { listOf(it, "$it.json") }
        ))
        assertTrue(result.succeeded())
        assertEquals(listOf(first), result.result!!.single().binaries.map { it.`package`.name })
        assertEquals(source, result.result!!.single().source!!.link)
        excluded.forEach { name -> coVerify(exactly = 0) { client.getUrl("$name.json") } }
        coVerify(exactly = 1) { client.getUrl("$source.json") }
    }

    @Test
    fun `metadata free Semeru retains name based fallback`() = runBlocking {
        val assets = SemeruMapperTest.allGhAssets.filterNot { it.name.endsWith(".json") }
        val release = upstream().copy(name = "jdk8u302-b08_openj9-0.27.0",
            releaseAssets = GHAssets(assets, PageInfo(false, ""), assets.size))
        val result = factory(client()).get(Vendor.ibm).toAdoptRelease(release)
        assertTrue(result.succeeded())
        assertEquals("upstream", result.result!!.single().id)
        assertEquals(1, result.result!!.single().binaries.size)
    }

    @Test
    fun `selector overlap fetches once and replaces every old split without prefix collisions`() = runBlocking {
        val upstream = upstream()
        val mapper = factory(client()).get(Vendor.eclipse)
        val previous = mapper.toAdoptRelease(upstream).result!!
        val unrelated = mapper.toAdoptRelease(upstream.copy(id = GitHubId("upstream-other"))).result!!
        val repository = repository(upstream)
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(GHRepository(GHReleases(
            listOf(upstream, upstream, upstream.copy(id = GitHubId("upstream-other"))), PageInfo(false, "")
        )))
        coEvery { repository.getSummary(8) } returns summary
        val refreshed = mapper.toAdoptRelease(upstream(listOf(first, "$first.json"))).result!!
        coEvery { repository.getReleaseById(upstream.id) } returns ReleaseResult(refreshed)
        coEvery { repository.getReleaseById(GitHubId("upstream-other")) } returns ReleaseResult(error = "Network failure")
        val after = AdoptReposBuilder(repository, mockk()).incrementalUpdate(setOf(upstream.name), repos(previous + unrelated)) {
            GHReleaseMetadata(999, it)
        }
        assertEquals((refreshed + unrelated).associateBy { it.id }, nodes(after))
        coVerify(exactly = 1) { repository.getReleaseById(upstream.id) }
    }

    @Test
    fun `new upstream ID is not masked by an existing ID with the same prefix`() = runBlocking {
        val old = DateTimeFormatter.ISO_INSTANT.format(TimeSource.now().minusDays(30))
        val upstream = upstream().copy(publishedAt = old, updatedAt = old)
        val other = upstream.copy(id = GitHubId("upstream-other"))
        val mapper = factory(client()).get(Vendor.eclipse)
        val previous = mapper.toAdoptRelease(other).result!!
        val refreshed = mapper.toAdoptRelease(upstream).result!!
        val repository = repository(upstream)
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHRepository(GHReleases(listOf(upstream, other), PageInfo(false, "")))
        )
        coEvery { repository.getReleaseById(upstream.id) } returns ReleaseResult(refreshed)
        val after = AdoptReposBuilder(repository, mockk()).incrementalUpdate(emptySet(), repos(previous)) { null }
        assertEquals((previous + refreshed).associateBy { it.id }, nodes(after))
        coVerify(exactly = 1) { repository.getReleaseById(upstream.id) }
        coVerify(exactly = 0) { repository.getReleaseById(other.id) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["jdk17u-2022-05-27-19-32-beta", "jdk-11.0.13'+8"])
    fun `known malformed tags are permanent policy exclusions in both update paths`(name: String) = runBlocking {
        val github = mockk<GitHubApi>()
        val excluded = upstream().copy(name = name, isPrerelease = true)
        coEvery { github.getReleaseById(excluded.id) } returns excluded
        coEvery { github.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(if (secondArg<String>() == "temurin8-binaries") listOf(excluded) else emptyList(), PageInfo(false, "")))
        }
        val client = client()
        val repository = AdoptRepositoryImpl(github, factory(client))
        val outcome = repository.getReleaseById(excluded.id)!!
        assertFalse(outcome.succeeded())
        assertFalse(outcome.retryable)
        assertTrue(repository.getRelease(8, ReleaseIncludeFilter.INCLUDE_ALL).releases.nodes.isEmpty())
        coVerify(exactly = 0) { client.getUrl(any()) }
    }

    @Test
    fun `successful empty and explicit exclusion remove old splits but null and exceptions preserve them`() = runBlocking {
        val upstream = upstream()
        val previous = factory(client()).get(Vendor.eclipse).toAdoptRelease(upstream).result!!
        val before = repos(previous)
        val repository = repository(upstream)
        val builder = AdoptReposBuilder(repository, mockk())
        coEvery { repository.getReleaseById(upstream.id) } returns null
        assertEquals(nodes(before), nodes(builder.incrementalUpdate(emptySet(), before) { null }))
        coEvery { repository.getReleaseById(upstream.id) } throws IllegalStateException("Network failure")
        assertEquals(nodes(before), nodes(builder.incrementalUpdate(emptySet(), before) { null }))
        coEvery { repository.getReleaseById(upstream.id) } returns ReleaseResult(emptyList())
        assertTrue(nodes(builder.incrementalUpdate(emptySet(), before) { null }).isEmpty())
        coEvery { repository.getReleaseById(upstream.id) } returns ReleaseResult(error = "Explicit exclusion", retryable = false)
        assertTrue(nodes(builder.incrementalUpdate(emptySet(), before) { null }).isEmpty())
        val excluded = upstream.copy(name = "jdk17u-2022-05-27-19-32-beta", isPrerelease = true)
        assertEquals(emptyList<Release>(), factory(client()).get(Vendor.eclipse).toAdoptRelease(excluded).result)
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "invalid", "exception", "missing-node", "missing-sbom-node"])
    fun `full builds abort incomplete snapshots and recover`(failure: String) = runBlocking {
        val client = client()
        val factory = factory(client)
        val secondAsset = if (failure == "missing-sbom-node") sbom else second
        val sidecar = if (secondAsset == sbom) sbom.removeSuffix(".json") + "-metadata.json" else "$second.json"
        val complete = upstream(listOf(first, "$first.json", secondAsset, sidecar))
        var current = complete
        val github = mockk<GitHubApi>()
        coEvery { github.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(if (secondArg<String>() == "temurin8-binaries") listOf(current) else emptyList(), PageInfo(false, "")))
        }
        coEvery { github.getRepositorySummary(any(), any()) } answers {
            GHSummaryTestDataGenerator.generateGHRepositorySummary(GHRepository(GHReleases(
                if (secondArg<String>() == "temurin8-binaries") listOf(current) else emptyList(), PageInfo(false, "")
            )))
        }
        coEvery { github.getReleaseById(complete.id) } answers { current }
        val versions = mockk<UpdatableVersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        every { versions.getLtsVersions() } returns arrayOf(8)
        every { versions.getTipVersion() } returns 8
        coEvery { versions.updateVersions() } returns Unit
        val builder = AdoptReposBuilder(AdoptRepositoryImpl(github, factory), versions)
        val previous = builder.build(ReleaseIncludeFilter.INCLUDE_ALL)
        assertEquals(2, nodes(previous).size)
        if (failure.endsWith("node")) current = upstream(listOf(first, "$first.json", secondAsset))
        else coEvery { client.getUrl(sidecar) } answers {
            when (failure) {
                "missing" -> null
                "invalid" -> "not JSON"
                else -> error("Temporary network failure")
            }
        }
        assertThrows(IllegalStateException::class.java) { runBlocking { builder.build(ReleaseIncludeFilter.INCLUDE_ALL) } }
        val database = spyk(InMemoryApiPersistence(previous, mockk()))
        val updater = V3Updater(builder, mockk(), APIDataStoreImpl(database), database, mockk(relaxed = true),
            ReleaseVersionResolver(versions), mockk(relaxed = true), versions)
        // No download statistics: the stats guard cannot hide a partial snapshot.
        assertNull(database.getLatestGithubStatsForFeatureVersion(8))
        assertEquals(previous, updater.runUpdate(previous, AtomicBoolean(true), mockk()))
        assertEquals(nodes(previous), nodes(database.repos))
        coVerify(exactly = 0) { database.updateAllRepos(any(), any()) }
        current = complete
        coEvery { client.getUrl(sidecar) } returns metadata(2)
        assertEquals(nodes(previous), nodes(builder.build(ReleaseIncludeFilter.INCLUDE_ALL)))
    }
}



