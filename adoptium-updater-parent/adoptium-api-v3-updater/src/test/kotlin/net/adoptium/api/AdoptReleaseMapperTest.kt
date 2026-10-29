package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.ReleaseVersionResolver
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.HttpClientFactory
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.stats.GitHubDownloadStatsCalculator
import net.adoptium.api.v3.stats.StatsInterface
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@AddPackages(value = [DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class AdoptReleaseMapperTest : BaseTest() {

    val jdk = GHAsset(
        name = "OpenJDK8U-jre_x64_linux_hotspot-123244354325.tar.gz",
        size = 1L,
        downloadUrl = "",
        downloadCount = 1L,
        updatedAt = "2013-02-27T19:35:32Z"
    )

    val checksum = GHAsset(
        name = "OpenJDK8U-jre_x64_linux_hotspot-123244354325.tar.gz.sha256.txt",
        size = 1L,
        downloadUrl = "",
        downloadCount = 1L,
        updatedAt = "2013-02-27T19:35:32Z"
    )

    @Test
    fun `unparsable version is non-retryable but not remembered once corrected`() {
        runBlocking {
            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = GHRelease(
                id = GitHubId("1"),
                name = "OpenJDK 123244354325",
                isPrerelease = true,
                publishedAt = "2013-02-27T19:35:32Z",
                updatedAt = "2013-02-27T19:35:32Z",
                releaseAssets = source,
                resourcePath = "8",
                url = "a-url"
            )

            val mapper = createAdoptReleaseMapper()
            val result = mapper.toAdoptRelease(ghRelease)
            assertFalse(result.succeeded())
            assertFalse(result.retryable)
            assertNotNull(result.error)
            assertNull(result.result)
            // The mapper keeps no per-release memory; the builder's exclusion set bounds repeat attempts.
            assertEquals(result.error, mapper.toAdoptRelease(ghRelease).error)
            val corrected = mapper.toAdoptRelease(ghRelease.copy(name = "jdk8u242-b02"))
            assertTrue(corrected.succeeded())
        }
    }

    @Test
    fun `policy exclusions are evaluated from the current snapshot without blacklisting its ID`() = runBlocking {
        val assets = buildGhAssets(listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1"
        ))
        val upstream = formGhRelease(assets)
        val mapper = createAdoptReleaseMapper(mockClient())
        val excluded = mapper.toAdoptRelease(upstream.copy(name = "jdk17u-2022-05-27-19-32-beta"))

        assertTrue(excluded.succeeded())
        assertTrue(excluded.result!!.isEmpty())
        val corrected = mapper.toAdoptRelease(upstream)
        assertTrue(corrected.succeeded())
        assertEquals(1, corrected.result!!.single().binaries.size)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `binary mapping failures remain retryable with and without metadata`(metadata: Boolean) = runBlocking {
        val names = listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1"
        ).filter { metadata || !it.first.endsWith(".json") }
        val assets = buildGhAssets(names)
        val brokenAssets = assets.copy(assets = assets.assets.map {
            if (it.name.endsWith(".tar.gz")) it.copy(updatedAt = "invalid-date") else it
        })
        val mapper = createAdoptReleaseMapper(mockClient())
        val failed = mapper.toAdoptRelease(formGhRelease(brokenAssets))

        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        val recovered = mapper.toAdoptRelease(formGhRelease(assets))
        assertTrue(recovered.succeeded())
        assertEquals(1, recovered.result!!.single().binaries.size)
    }

    @Test
    fun statsIgnoresNonBinaryAssets() {
        runBlocking {
            val source = GHAssets(listOf(jdk, checksum), PageInfo(false, ""), 2)

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals(1, release.result!!.first().download_count)
        }
    }

    @Test
    fun obeysReleaseTypeforBinaryRepos() {
        runBlocking {

            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals(ReleaseType.ea, release.result!!.first().release_type)
        }
    }

    @Test
    fun copesWithSbom() {
        runBlocking {
            val source = buildGhAssets(
                listOf(
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json", "1"),
                    Pair("OpenJDK8U-sbom_aarch64_alpine-linux_hotspot_2022-06-17-23-30.json", ""),
                    Pair("OpenJDK8U-sbom_aarch64_alpine-linux_hotspot_2022-06-17-23-30-metadata.json", "2"),
                )
            )
            val ghRelease = formGhRelease(source)
            val release = createAdoptReleaseMapper(mockClient()).toAdoptRelease(ghRelease)

            assertTrue(release.succeeded())
            assertFalse(release.retryable)
            assertEquals(2, release.result?.size)
            assertEquals(source.assets.filterNot { it.name.endsWith("-metadata.json") || it.name.endsWith(".tar.gz.json") }
                .map { it.name }.toSet(), release.result!!.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ascending", "descending"])
    fun `sbom metadata only matches its exact sidecar when build numbers share a prefix`(order: String) = runBlocking {
        val sbom1 = "OpenJDK8U-sbom_aarch64_linux_hotspot_21.0.9_1.json"
        val sbom10 = "OpenJDK8U-sbom_aarch64_linux_hotspot_21.0.9_10.json"
        val assets = listOf(
            Pair(sbom1, ""),
            Pair(sbom1.removeSuffix(".json") + "-metadata.json", "1"),
            Pair(sbom10, ""),
            Pair(sbom10.removeSuffix(".json") + "-metadata.json", "10")
        )
        val source = buildGhAssets(if (order == "ascending") assets else assets.reversed())

        val release = createAdoptReleaseMapper(mockClient()).toAdoptRelease(formGhRelease(source))

        assertTrue(release.succeeded())
        assertEquals(2, release.result!!.size)
        assertEquals(
            setOf(sbom1, sbom10),
            release.result!!.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet()
        )
        assertTrue(release.result!!.all { it.binaries.size == 1 })
    }

    @Test
    fun copesWithMultipleVersionsInSingleRelease() {
        runBlocking {

            val source = buildGhAssets(
                listOf(
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json", "1"),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json", "2"),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-3.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-3.tar.gz.json", "2"),
                )
            )
            val ghRelease = formGhRelease(source)

            val client = mockClient()

            val release = createAdoptReleaseMapper(client).toAdoptRelease(ghRelease)

            assertEquals(2, release.result!!.size)
            assertEquals(1, release.result!![0].binaries.size)
            assertEquals(2, release.result!![1].binaries.size)

            assertEquals(1, release.result!![0].download_count)
            assertEquals(2, release.result!![1].download_count)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "invalid", "exception", "missing-node", "missing-sbom-node", "invalid-variant"])
    fun `incomplete metadata refresh preserves both stored split groups`(failure: String) = runBlocking {
        val secondAsset = if (failure == "missing-sbom-node") {
            "OpenJDK8U-sbom_aarch64_alpine-linux_hotspot_2022-06-17-23-30.json"
        } else "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz"
        val secondMetadata = if (failure == "missing-sbom-node") {
            secondAsset.removeSuffix(".json") + "-metadata.json"
        } else "$secondAsset.json"
        val assetNames = listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            secondAsset to "",
            secondMetadata to "2"
        )
        val assets = buildGhAssets(assetNames)
        val upstream = formGhRelease(assets)
        val cached = CachedGitHubHtmlClient(InMemoryInternalDbStore(), mockClient())
        var fail = false
        val client = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? = cached.getUrl(url)
            override suspend fun getAsset(asset: GHAsset): String? {
                if (fail && asset.downloadUrl == "2") {
                    return when (failure) {
                        "missing" -> null
                        "invalid" -> "not JSON"
                        "invalid-variant" -> cached.getAsset(asset)!!.replace("\"variant\": \"openj9\"", "\"variant\": \"unknown\"")
                        else -> throw IllegalStateException("Metadata retrieval failed")
                    }
                }
                return cached.getAsset(asset)
            }
        }
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val previous = mapper.toAdoptRelease(upstream).result!!
        assertEquals(2, previous.size)
        val expectedBinaries = setOf(assetNames.first().first, secondAsset)
        assertEquals(expectedBinaries, previous.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet())
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(previous))))
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before))
        fail = true
        val incomplete = if (failure == "missing-node" || failure == "missing-sbom-node") {
            formGhRelease(buildGhAssets(assetNames.dropLast(1)))
        } else upstream
        val failed = mapper.toAdoptRelease(incomplete)
        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        coEvery { repository.getReleaseById(upstream.id) } returns failed

        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })
        val after = builder.incrementalUpdate(setOf(upstream.name), before) { null }

        assertEquals(previous.associateBy { it.id }, after.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 1) { repository.getReleaseById(upstream.id) }
        fail = false
        val recovered = mapper.toAdoptRelease(upstream)
        assertTrue(recovered.succeeded())
        assertFalse(recovered.retryable)
        assertEquals(2, recovered.result!!.size)
        assertEquals(expectedBinaries, recovered.result!!.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet())
        coEvery { repository.getReleaseById(upstream.id) } returns recovered

        val retried = builder.incrementalUpdate(setOf(upstream.name), after) { null }

        assertEquals(recovered.result!!.associateBy { it.id }, retried.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 2) { repository.getReleaseById(upstream.id) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["metadata", "legacy"])
    fun `ungrouped downloads are counted once regardless of asset order`(kind: String) = runBlocking {
        val names = listOf(
            "OpenJDK8U-jre_x64_windows_hotspot-1.zip" to "",
            "OpenJDK8U-jre_x64_windows_hotspot-1.zip.json" to "1",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json" to "2",
            "OpenJDK8U-jre_x64_windows_hotspot-1.msi" to "",
            "standalone.pkg" to "",
            "corretto-1.tar.gz" to "",
            "OpenJDK8U-sources-1.tar.gz" to "",
            "unsupported.tar.xz" to "",
            "checksum.sha256.txt" to ""
        )
        val counts = listOf(10L, 100L, 11L, 100L, 20L, 30L, 40L, 50L, 60L, 70L)
        val assets = buildGhAssets(names).assets.mapIndexed { index, asset -> asset.copy(downloadCount = counts[index]) }
            .filterNot { kind == "legacy" && it.name.endsWith(".json") }
        val mapper = createAdoptReleaseMapper(mockClient())

        for (ordered in listOf(assets, assets.reversed())) {
            val result = mapper.toAdoptRelease(formGhRelease(GHAssets(ordered, PageInfo(false, ""), ordered.size)))

            assertTrue(result.succeeded())
            assertFalse(result.retryable)
            assertEquals(161L, result.result!!.sumOf { it.download_count })
            assertEquals(161L, GitHubDownloadStatsCalculator.getStats(
                AdoptRepos(listOf(FeatureRelease(8, Releases(result.result!!))))
            ).single().downloads)
            if (kind == "metadata") {
                assertEquals(listOf(150L, 11L), result.result!!.map { it.download_count })
                assertEquals(listOf(1, 2), result.result!!.map { it.version_data.build })
            } else {
                assertEquals(1, result.result!!.size)
            }
            assertEquals(2, result.result!!.sumOf { it.binaries.size })
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "invalid", "exception"])
    fun `metadata completeness ignores sources installers unsupported and excluded assets`(failure: String) = runBlocking {
        val assets = buildGhAssets(listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            "OpenJDK8U-sources-1.tar.gz" to "sources",
            "OpenJDK8U-sources-1.tar.gz.json" to "source-metadata",
            "OpenJDK8U-jre_x64_windows_hotspot-1.msi" to "installer",
            "OpenJDK8U-jre_x64_windows_hotspot-1.msi.json" to "ignored-metadata",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.xz" to "unsupported",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.xz.json" to "ignored-metadata",
            "corretto-1.tar.gz" to "excluded",
            "corretto-1.tar.gz.json" to "ignored-metadata",
            "AQAvitTapFiles-1.tar.gz" to "excluded",
            "AQAvitTapFiles-1.tar.gz.json" to "ignored-metadata"
        ))
        val cached = CachedGitHubHtmlClient(InMemoryInternalDbStore(), mockClient())
        val metadata = cached.getUrl("1")!!
        var sourceMetadataFetched = false
        val client = spyk(object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? = cached.getUrl(url)
            override suspend fun getAsset(asset: GHAsset): String? = when (asset.downloadUrl) {
                "ignored-metadata" -> when (failure) {
                    "missing" -> null
                    "invalid" -> "not JSON"
                    else -> throw IllegalStateException("Ignored metadata retrieval failed")
                }
                "source-metadata" -> {
                    sourceMetadataFetched = true
                    metadata.replace("\"binary_type\": \"jre\"", "\"binary_type\": \"sources\"")
                }
                "1" -> metadata
                else -> cached.getAsset(asset)
            }
        })
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val result = mapper.toAdoptRelease(formGhRelease(assets))

        assertTrue(result.succeeded())
        assertFalse(result.retryable)
        assertEquals(setOf("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz", "OpenJDK8U-sources-1.tar.gz"),
            result.result!!.single().binaries.map { it.`package`.name }.toSet())
        assertEquals("sources", result.result!!.single().source!!.link)
        assertTrue(sourceMetadataFetched)
        coVerify(exactly = 0) { client.getAsset(match { it.downloadUrl == "ignored-metadata" }) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["legacy", "semeru"])
    fun `metadata free legacy release still falls back to names`(kind: String) = runBlocking {
        val upstream = if (kind == "semeru") {
            val assets = SemeruMapperTest.allGhAssets.filterNot { it.name.endsWith(".json") }
            formGhRelease(GHAssets(assets, PageInfo(false, ""), assets.size)).copy(
                name = "jdk8u302-b08_openj9-0.27.0",
                url = "https://github.com/ibmruntimes/semeru8-binaries/releases/tag/jdk8u302-b08_openj9-0.27.0"
            )
        } else formGhRelease(buildGhAssets(listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to ""
        )))
        val cached = CachedGitHubHtmlClient(InMemoryInternalDbStore(), mockkHttpClient())
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(cached), cached)
            .get(if (kind == "semeru") Vendor.ibm else Vendor.eclipse)
        val result = mapper.toAdoptRelease(upstream)

        assertTrue(result.succeeded())
        assertFalse(result.retryable)
        assertEquals("1", result.result!!.single().id)
        assertEquals(if (kind == "semeru") 1 else 2, result.result!!.single().binaries.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ea-omitted", "ga-omitted", "ea-decreased", "ga-decreased"])
    fun `full update stats allowance applies only to omitted EAs`(scenario: String) = runBlocking {
        // Full updates never rewrite the AdoptOpenJDK ecosystem's default vendor, so the
        // statistics allowance cannot apply there.
        assumeTrue(ReleaseIncludeFilter(TimeSource.now(), ReleaseFilterType.ALL).filterVendor(Vendor.getDefault()))
        val timestamp = TimeSource.now().minusDays(1)
        fun release(id: String, type: ReleaseType, downloads: Long) = Release(
            id, type, "link", id, DateTime(timestamp), DateTime(timestamp), emptyArray(),
            downloads, Vendor.getDefault(), VersionData(8, 0, 242, null, null, 2, null, "")
        )
        val ga = release("ga", ReleaseType.ga, 100)
        val ea = release("ea", ReleaseType.ea, 20)
        fun repo(releases: List<Release>) = AdoptRepos(listOf(FeatureRelease(8, Releases(releases))))
        val before = repo(listOf(ga, ea))
        val replacements = when (scenario) {
            "ea-omitted" -> listOf(ga)
            "ga-omitted" -> listOf(ea)
            "ea-decreased" -> listOf(ga, release("ea", ReleaseType.ea, 10))
            else -> listOf(release("ga", ReleaseType.ga, 90), ea)
        }
        val builder = mockk<AdoptReposBuilder>()
        coEvery { builder.build(any()) } answers {
            val filter = firstArg<ReleaseIncludeFilter>()
            repo(replacements.filter { filter.filter(it.vendor, timestamp, it.release_type == ReleaseType.ea) })
        }
        coEvery { builder.incrementalUpdate(any(), any(), any()) } answers { secondArg<AdoptRepos>() }
        val versions = mockk<UpdatableVersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        every { versions.getLtsVersions() } returns arrayOf(8)
        every { versions.getTipVersion() } returns 8
        coEvery { versions.updateVersions() } returns Unit
        val database = spyk(InMemoryApiPersistence(before, mockk()))
        database.addGithubDownloadStatsEntries(GitHubDownloadStatsCalculator.getStats(before))
        val updater = V3Updater(
            builder, mockk(), APIDataStoreImpl(database), database, mockk(relaxed = true),
            ReleaseVersionResolver(versions), mockk(relaxed = true), versions
        )

        val after = updater.runUpdate(before, AtomicBoolean(true), mockk())

        val expected = if (scenario == "ea-omitted") repo(replacements) else before
        assertEquals(expected, after)
        assertEquals(expected, database.repos)
        val writes = when (scenario) {
            "ea-omitted" -> 2
            "ea-decreased" -> 1
            else -> 0
        }
        coVerify(exactly = writes) { database.updateAllRepos(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ea-growth-masks-ga-loss", "stored-ea-hidden-from-api-view", "stale-saved-stats"])
    fun `full update stats guard uses the stored snapshot`(scenario: String) = runBlocking {
        assumeTrue(ReleaseIncludeFilter(TimeSource.now(), ReleaseFilterType.ALL).filterVendor(Vendor.getDefault()))
        val timestamp = TimeSource.now().minusDays(1)
        fun release(id: String, type: ReleaseType, downloads: Long) = Release(
            id, type, "link", id, DateTime(timestamp), DateTime(timestamp), emptyArray(),
            downloads, Vendor.getDefault(), VersionData(8, 0, 242, null, null, 2, null, "")
        )
        fun repo(releases: List<Release>) = AdoptRepos(listOf(FeatureRelease(8, Releases(releases))))
        // Saved statistics come from the last full update. Later incremental refreshes only reach the
        // stored snapshot, and releases without binaries are stored but hidden from the in-memory view.
        // A stale saved total (failed stats write after an EA omission) must not block a rebuild.
        val stale = scenario == "stale-saved-stats"
        val savedStats = repo(listOf(release("ga", ReleaseType.ga, 90), release("ea", ReleaseType.ea, if (stale) 30 else 10)))
        val masked = scenario == "ea-growth-masks-ga-loss"
        val stored = if (masked) {
            repo(listOf(release("ga", ReleaseType.ga, 90), release("ea", ReleaseType.ea, 50)))
        } else if (stale) {
            repo(listOf(release("ga", ReleaseType.ga, 90)))
        } else {
            repo(listOf(release("ga", ReleaseType.ga, 90), release("ea", ReleaseType.ea, 10)))
        }
        val current = if (masked) stored else repo(listOf(release("ga", ReleaseType.ga, 90)))
        val rebuilt = repo(listOf(release("ga", ReleaseType.ga, if (masked) 60 else 90)))
        val builder = mockk<AdoptReposBuilder>()
        coEvery { builder.build(any()) } answers {
            val filter = firstArg<ReleaseIncludeFilter>()
            AdoptRepos(rebuilt.repos.values.map { featureRelease ->
                FeatureRelease(featureRelease.featureVersion, Releases(featureRelease.releases.getReleases()
                    .filter { filter.filter(it.vendor, timestamp, it.release_type == ReleaseType.ea) }.toList()))
            })
        }
        coEvery { builder.incrementalUpdate(any(), any(), any()) } answers { secondArg<AdoptRepos>() }
        val versions = mockk<UpdatableVersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        every { versions.getLtsVersions() } returns arrayOf(8)
        every { versions.getTipVersion() } returns 8
        coEvery { versions.updateVersions() } returns Unit
        val database = spyk(InMemoryApiPersistence(stored, mockk()))
        database.addGithubDownloadStatsEntries(GitHubDownloadStatsCalculator.getStats(savedStats))
        val stats = mockk<StatsInterface>(relaxed = true)
        coEvery { stats.update(any()) } coAnswers {
            database.addGithubDownloadStatsEntries(GitHubDownloadStatsCalculator.getStats(firstArg()))
        }
        val updater = V3Updater(
            builder, mockk(), APIDataStoreImpl(database), database, stats,
            ReleaseVersionResolver(versions), mockk(relaxed = true), versions
        )

        val after = updater.runUpdate(current, AtomicBoolean(true), mockk())

        if (masked) {
            assertEquals(current, after)
            assertEquals(stored, database.repos)
            coVerify(exactly = 0) { database.updateAllRepos(any(), any()) }
        } else {
            assertEquals(rebuilt, after)
            assertEquals(rebuilt, database.repos)
            coVerify(exactly = 2) { database.updateAllRepos(any(), any()) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "missing-node", "invalid-variant", "ea-missing", "ea-missing-node", "ea-invalid-variant"])
    fun `full update omits failed EAs but aborts GA metadata failures and recovers`(scenario: String) = runBlocking {
        val ea = scenario.startsWith("ea-")
        val failure = scenario.removePrefix("ea-")
        val updated = if (ea) TimeSource.now().minusDays(1).toInstant().toString() else "2013-02-27T19:35:32Z"
        val assetNames = listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json" to "2"
        )
        fun upstream(incomplete: Boolean) = GHRelease(
            GitHubId("1"), "jdk8u242-b02", ea,
            updated, updated,
            buildGhAssets(if (incomplete && failure == "missing-node") assetNames.dropLast(1) else assetNames),
            "8", "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u242-b02"
        )
        val cached = CachedGitHubHtmlClient(InMemoryInternalDbStore(), mockClient())
        var fail = false
        val metadata = mutableMapOf<String, String?>()
        val client = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? = cached.getUrl(url)
            override suspend fun getAsset(asset: GHAsset): String? {
                if (asset.downloadUrl == "healthy-2") {
                    return metadata.getOrPut("healthy-2") { cached.getAsset(asset.copy(downloadUrl = "2")) }
                }
                if (fail && failure == "missing" && asset.downloadUrl == "2") return null
                val content = metadata.getOrPut(asset.name) { cached.getAsset(asset) }
                return if (fail && failure == "invalid-variant" && asset.downloadUrl == "2") {
                    content?.replace("\"variant\": \"openj9\"", "\"variant\": \"unknown\"")
                } else content
            }
        }
        val factory = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client)
        val healthy = upstream(false).let {
            it.copy(id = GitHubId("healthy"), isPrerelease = false, releaseAssets = it.releaseAssets.copy(
                assets = it.releaseAssets.assets.map { asset ->
                    if (asset.downloadUrl == "2") asset.copy(downloadUrl = "healthy-2") else asset
                }
            ))
        }
        val healthyMapped = factory.get(Vendor.eclipse).toAdoptRelease(healthy).result!!
        val previous = factory.get(Vendor.eclipse).toAdoptRelease(upstream(false)).result!! + healthyMapped
        assertEquals(4, previous.size)
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(previous))))
        val gitHub = mockk<GitHubApi>()
        coEvery { gitHub.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(if (secondArg<String>() == "temurin8-binaries") listOf(upstream(fail), healthy) else emptyList(), PageInfo(false, "")))
        }
        coEvery { gitHub.getRepositorySummary(any(), any()) } answers {
            GHSummaryTestDataGenerator.generateGHRepositorySummary(GHRepository(GHReleases(
                if (secondArg<String>() == "temurin8-binaries") listOf(upstream(fail), healthy) else emptyList(), PageInfo(false, "")
            )))
        }
        coEvery { gitHub.getReleaseById(GitHubId("1")) } answers { upstream(fail) }
        coEvery { gitHub.getReleaseById(GitHubId("healthy")) } returns healthy
        val versions = mockk<UpdatableVersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(8)
        every { versions.getLtsVersions() } returns arrayOf(8)
        every { versions.getTipVersion() } returns 8
        coEvery { versions.updateVersions() } returns Unit
        val database = spyk(InMemoryApiPersistence(before, mockk()))
        val updater = V3Updater(
            AdoptReposBuilder(AdoptRepositoryImpl(gitHub, factory), versions), mockk(),
            APIDataStoreImpl(database), database, mockk(relaxed = true),
            ReleaseVersionResolver(versions), mockk(relaxed = true), versions
        )

        database.addGithubDownloadStatsEntries(GitHubDownloadStatsCalculator.getStats(before))
        // Download statistics only count the ecosystem's default vendor.
        assertEquals(if (Vendor.getDefault() == Vendor.eclipse) 4L else 0L,
            database.getLatestGithubStatsForFeatureVersion(8)!!.downloads)
        fail = true
        val after = updater.runUpdate(before, AtomicBoolean(true), mockk())

        val expected = (if (ea) healthyMapped else previous).associateBy { it.id }
        assertEquals(expected, after.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(expected, database.repos.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = if (ea) 2 else 0) { database.updateAllRepos(any(), any()) }

        fail = false
        val recovered = updater.runUpdate(after, AtomicBoolean(true), mockk())

        assertEquals(previous.associateBy { it.id }, recovered.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(recovered, database.repos)
        coVerify(exactly = if (ea) 4 else 2) { database.updateAllRepos(any(), any()) }
    }

    private fun mockClient() = object : UpdaterHtmlClient {
        override suspend fun get(url: String, log: Boolean): String {
            return getMetadata(url)
        }

        fun getMetadata(url: String): String {
            val opt = UUID.randomUUID()

            val build = try {
                Integer.parseInt(url)
            } catch (ignore: Exception) {
                1
            }

            return """
                            {
                                "WARNING": "THIS METADATA FILE IS STILL IN ALPHA DO NOT USE ME",
                                "os": "windows",
                                "arch": "x86-32",
                                "variant": "openj9",
                                "version": {
                                    "minor": 0,
                                    "security": 242,
                                    "pre": null,
                                    "adopt_build_number": 1,
                                    "major": 8,
                                    "version": "1.8.0_242-$opt-b0$build",
                                    "semver": "8.0.242+$build.1.$opt",
                                    "build": $build,
                                    "opt": "$opt"
                                },
                                "scmRef": "",
                                "version_data": "jdk8u",
                                "binary_type": "jre",
                                "sha256": "dc755cf762c867d4c71b782b338d2dc1500b468ab01adbf88620b5ae55eef42a"
                            }
                        """.trimIndent()
                .replace("\n", "")
        }

        override suspend fun getFullResponse(request: UrlRequest, log: Boolean): HttpResponse {

            val metadataResponse = mockk<HttpResponse>()

            val entity = mockk<HttpEntity>()
            every { entity.content } returns getMetadata(request.url).byteInputStream()
            every { metadataResponse.statusLine } returns BasicStatusLine(ProtocolVersion("", 1, 1), 200, "")
            every { metadataResponse.entity } returns entity
            every { metadataResponse.getFirstHeader("Last-Modified") } returns BasicHeader("Last-Modified", "Thu, 01 Jan 1970 00:00:00 GMT")
            return metadataResponse
        }
    }

    private fun buildGhAssets(assetNames: List<Pair<String, String>>) =
        GHAssets(assetNames
            .map {
                GHAsset(
                    it.first,
                    1L,
                    it.second,
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            },
            PageInfo(false, ""),
            assetNames.size
        )

    @Test
    fun updaterCopesWithExceptionFromGitHub() {
        runBlocking {

            val client = object : UpdaterHtmlClient {
                override suspend fun get(url: String, log: Boolean): String? {
                    throw RuntimeException("Failed to get metadata")
                }

                override suspend fun getFullResponse(request: UrlRequest, log: Boolean): HttpResponse? {
                    throw RuntimeException("Failed to get metadata")
                }
            }

            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = formGhRelease(source)

            createAdoptReleaseMapper(client).toAdoptRelease(ghRelease)
        }
    }

    @Test
    fun `identifies the source repo if present in a release`() {
        runBlocking {
            val source = GHAssets(
                listOf(
                    GHAsset(
                        "OpenJDK11U-sources_11.0.13_6_ea.tar.gz",
                        1L,
                        "the-source-link",
                        1L,
                        "2013-02-27T19:35:32Z"
                    )
                ),
                PageInfo(false, ""),
                1
            )

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals("the-source-link", release.result!!.first().source!!.link)
        }
    }

    private fun formGhRelease(source: GHAssets) = GHRelease(
        id = GitHubId("1"),
        name = "jdk9u-2018-09-27-08-50",
        isPrerelease = true,
        publishedAt = "2013-02-27T19:35:32Z",
        updatedAt = "2013-02-27T19:35:32Z",
        releaseAssets = source,
        resourcePath = "8",
        url = "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk9u-2018-09-27-08-50/OpenJDK9U-jre_aarch64_linux_hotspot_2018-09-27-08-50.tar.gz"
    )
}
