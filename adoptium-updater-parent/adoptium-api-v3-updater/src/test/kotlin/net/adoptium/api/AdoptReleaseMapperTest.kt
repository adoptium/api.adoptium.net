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
import net.adoptium.api.v3.models.Vendor
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
    fun ignoresUnparsableVersion() {
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

            val result = createAdoptReleaseMapper().toAdoptRelease(ghRelease)
            assertFalse(result.succeeded())
            assertFalse(result.retryable)
            assertNotNull(result.error)
            assertNull(result.result)
        }
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

            assertEquals(2, release.result?.size)
        }
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
    @ValueSource(strings = ["missing", "invalid", "exception", "missing-node"])
    fun `incomplete metadata refresh preserves both stored split groups`(failure: String) = runBlocking {
        val assets = buildGhAssets(listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json" to "2"
        ))
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
                        else -> throw IllegalStateException("Metadata retrieval failed")
                    }
                }
                return cached.getAsset(asset)
            }
        }
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val previous = mapper.toAdoptRelease(upstream).result!!
        assertEquals(2, previous.size)
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(previous))))
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before))
        fail = true
        val incomplete = if (failure == "missing-node") {
            formGhRelease(buildGhAssets(listOf(
                "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
                "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
                "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to ""
            )))
        } else upstream
        val failed = mapper.toAdoptRelease(incomplete)
        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        coEvery { repository.getReleaseById(upstream.id) } returns failed

        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier>())
        val after = builder.incrementalUpdate(setOf(upstream.name), before) { null }

        assertEquals(previous.associateBy { it.id }, after.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 1) { repository.getReleaseById(upstream.id) }
        fail = false
        val recovered = mapper.toAdoptRelease(upstream)
        assertTrue(recovered.succeeded())
        assertFalse(recovered.retryable)
        assertEquals(2, recovered.result!!.size)
        coEvery { repository.getReleaseById(upstream.id) } returns recovered

        val retried = builder.incrementalUpdate(setOf(upstream.name), after) { null }

        assertEquals(recovered.result!!.associateBy { it.id }, retried.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 2) { repository.getReleaseById(upstream.id) }
    }

    @Test
    fun `metadata completeness ignores sources installers unsupported and excluded assets`() = runBlocking {
        val assets = buildGhAssets(listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            "OpenJDK8U-sources-1.tar.gz" to "sources",
            "OpenJDK8U-jre_x64_windows_hotspot-1.msi" to "installer",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.xz" to "unsupported",
            "corretto-1.tar.gz" to "excluded",
            "AQAvitTapFiles-1.tar.gz" to "excluded"
        ))
        val result = createAdoptReleaseMapper(mockClient()).toAdoptRelease(formGhRelease(assets))

        assertTrue(result.succeeded())
        assertFalse(result.retryable)
        assertEquals(1, result.result!!.single().binaries.size)
        assertEquals("sources", result.result!!.single().source!!.link)
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
    @ValueSource(strings = ["missing", "missing-node"])
    fun `full update aborts on retryable metadata failure and recovers`(failure: String) = runBlocking {
        val assetNames = listOf(
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json" to "1",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz" to "",
            "OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json" to "2"
        )
        fun upstream(incomplete: Boolean) = GHRelease(
            GitHubId("1"), "jdk8u242-b02", false,
            "2013-02-27T19:35:32Z", "2013-02-27T19:35:32Z",
            buildGhAssets(if (incomplete && failure == "missing-node") assetNames.dropLast(1) else assetNames),
            "8", "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u242-b02"
        )
        val cached = CachedGitHubHtmlClient(InMemoryInternalDbStore(), mockClient())
        var fail = false
        val metadata = mutableMapOf<String, String?>()
        val client = object : GitHubHtmlClient {
            override suspend fun getUrl(url: String): String? = cached.getUrl(url)
            override suspend fun getAsset(asset: GHAsset): String? =
                if (fail && failure == "missing" && asset.downloadUrl == "2") null
                else metadata.getOrPut(asset.name) { cached.getAsset(asset) }
        }
        val factory = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client)
        val previous = factory.get(Vendor.eclipse).toAdoptRelease(upstream(false)).result!!
        assertEquals(2, previous.size)
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(previous))))
        val gitHub = mockk<GitHubApi>()
        coEvery { gitHub.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(if (secondArg<String>() == "temurin8-binaries") listOf(upstream(fail)) else emptyList(), PageInfo(false, "")))
        }
        coEvery { gitHub.getRepositorySummary(any(), any()) } answers {
            GHSummaryTestDataGenerator.generateGHRepositorySummary(GHRepository(GHReleases(
                if (secondArg<String>() == "temurin8-binaries") listOf(upstream(fail)) else emptyList(), PageInfo(false, "")
            )))
        }
        coEvery { gitHub.getReleaseById(GitHubId("1")) } answers { upstream(fail) }
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

        // No recorded download stats: the stats guard cannot protect these splits.
        assertNull(database.getLatestGithubStatsForFeatureVersion(8))
        fail = true
        val after = updater.runUpdate(before, AtomicBoolean(true), mockk())

        assertEquals(before, after)
        assertEquals(previous.associateBy { it.id }, database.repos.getFeatureRelease(8)!!.releases.nodes)
        coVerify(exactly = 0) { database.updateAllRepos(any(), any()) }
        coVerify(exactly = 1) { database.getLatestGithubStatsForFeatureVersion(any()) }

        fail = false
        val recovered = updater.runUpdate(after, AtomicBoolean(true), mockk())

        assertEquals(previous.associateBy { it.id }, recovered.getFeatureRelease(8)!!.releases.nodes)
        assertEquals(recovered, database.repos)
        coVerify(exactly = 2) { database.updateAllRepos(any(), any()) }
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
