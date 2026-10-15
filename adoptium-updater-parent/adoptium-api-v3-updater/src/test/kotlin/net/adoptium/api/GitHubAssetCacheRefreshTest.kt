package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.Vendor
import org.apache.http.HttpVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.HexFormat

class GitHubAssetCacheRefreshTest {

    @Test
    fun `changed asset contents are returned immediately despite recent cache check`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val store = InMemoryInternalDbStore()
        cache(store, "checksum", "old-checksum", now)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest("checksum")) } returns response("new-checksum", now.minusHours(1))
        val client = CachedGitHubHtmlClient(store, http)

        assertEquals("new-checksum", client.getUrl("checksum", now.minusHours(1)))
        assertEquals("new-checksum", store.getCachedWebpage("checksum")!!.data)
        assertEquals("new-checksum", client.getUrl("checksum", now.minusHours(1)))
        coVerify(exactly = 1) { http.getFullResponse(UrlRequest("checksum")) }
    }

    @Test
    fun `unchanged asset contents reuse the cache`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val store = InMemoryInternalDbStore()
        cache(store, "checksum", "cached-checksum", now)
        val http = mockk<UpdaterHtmlClient>()
        val client = CachedGitHubHtmlClient(store, http)

        assertEquals("cached-checksum", client.getUrl("checksum", now.minusHours(3)))
        coVerify(exactly = 0) { http.getFullResponse(any()) }
    }

    @Test
    fun `timestamp aware cache miss fetches asset contents`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val store = InMemoryInternalDbStore()
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest("checksum")) } returns response("new-checksum", now.minusHours(1))
        val client = CachedGitHubHtmlClient(store, http)

        assertEquals("new-checksum", client.getUrl("checksum", now.minusHours(1)))
        assertEquals("new-checksum", store.getCachedWebpage("checksum")!!.data)
    }

    @Test
    fun `replacement asset ID invalidates cache even with unchanged timestamp and absent digest`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val original = asset("checksum", now.minusDays(40), id = "original")
        val replacement = original.copy(id = "replacement")
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(original.downloadUrl)) } returnsMany listOf(
            response("old-checksum", now.minusDays(40)), response("new-checksum", now.minusDays(40))
        )
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)

        assertEquals("old-checksum", client.getAsset(original))
        assertEquals("new-checksum", client.getAsset(replacement))
        assertEquals("new-checksum", client.getAsset(replacement))
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(original.downloadUrl)) }
    }

    @Test
    fun `changed digest invalidates cache even with unchanged ID and timestamp`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val original = asset("checksum", now.minusDays(40), "same-id", digest("old-checksum"))
        val replacement = original.copy(digest = digest("new-checksum"))
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(original.downloadUrl)) } returnsMany listOf(
            response("old-checksum", now.minusDays(40)), response("new-checksum", now.minusDays(40))
        )
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)

        assertEquals("old-checksum", client.getAsset(original))
        assertEquals("new-checksum", client.getAsset(replacement))
        assertEquals("new-checksum", client.getAsset(replacement))
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(original.downloadUrl)) }
    }

    @Test
    fun `stale response bytes are retried before caching under the new digest`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val replacement = asset("checksum", now.minusDays(40), "replacement", digest("new-checksum"))
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(replacement.downloadUrl)) } returnsMany listOf(
            response("old-checksum", now), response("new-checksum", now)
        )
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)

        assertEquals("new-checksum", client.getAsset(replacement))
        assertEquals("new-checksum", client.getAsset(replacement))
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(replacement.downloadUrl)) }
    }

    @Test
    fun `assets without identity or digest do not trust URL-only cache entries`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val legacy = asset("checksum", now.minusDays(40))
        val store = InMemoryInternalDbStore()
        cache(store, legacy.downloadUrl, "stale-checksum", now)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(legacy.downloadUrl)) } returns response("new-checksum", now)
        val client = CachedGitHubHtmlClient(store, http)

        assertEquals("new-checksum", client.getAsset(legacy))
        assertEquals("new-checksum", client.getAsset(legacy))
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(legacy.downloadUrl)) }
    }

    @Test
    fun `updated metadata changes checksum when release timestamp and asset count stay unchanged`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val store = InMemoryInternalDbStore()
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", now.minusDays(5))
        val metadata = asset("${archive.name}.json", now.minusHours(3), "metadata-original", digest(metadata("old-checksum")))
        cache(store, metadata.downloadUrl, metadata("old-checksum"), now)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(metadata.downloadUrl)) } returnsMany listOf(
            response(metadata("old-checksum"), now.minusHours(3)),
            response(metadata("new-checksum"), now.minusHours(3))
        )
        val client = CachedGitHubHtmlClient(store, http)
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val releaseTime = DateTimeFormatter.ISO_INSTANT.format(now.minusDays(5))
        val release = GHRelease(
            GitHubId("rolling"), "jdk8u512-b05-ea-beta", true, releaseTime, releaseTime,
            GHAssets(listOf(archive, metadata), PageInfo(false, null), 2), "",
            "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u512-b05-ea-beta"
        )

        val before = mapper.toAdoptRelease(release).result!!.single()
        val updatedMetadata = metadata.copy(id = "metadata-replacement", digest = digest(metadata("new-checksum")))
        val after = mapper.toAdoptRelease(release.copy(
            releaseAssets = GHAssets(listOf(archive, updatedMetadata), PageInfo(false, null), 2)
        )).result!!.single()

        assertEquals("old-checksum", before.binaries.single().`package`.checksum)
        assertEquals("new-checksum", after.binaries.single().`package`.checksum)
        assertEquals(before.updated_at, after.updated_at)
        assertEquals(before.binaries.single().updated_at, after.binaries.single().updated_at)
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(metadata.downloadUrl)) }
    }

    @Test
    fun `archive and installer checksum files refresh by asset identity and digest`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val store = InMemoryInternalDbStore()
        val archive = asset("OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip", now.minusDays(5))
        val installer = asset(archive.name.removeSuffix(".zip") + ".msi", now.minusDays(5))
        val checksum = asset("${archive.name}.sha256.txt", now.minusDays(5), "archive-checksum", digest("new-archive ${archive.name}"))
        val installerChecksum = asset("${installer.name}.sha256.txt", now.minusDays(5), "installer-checksum", digest("new-installer ${installer.name}"))
        cache(store, checksum.downloadUrl, "old-archive ${archive.name}", now)
        cache(store, installerChecksum.downloadUrl, "old-installer ${installer.name}", now)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(checksum.downloadUrl)) } returns
            response("new-archive ${archive.name}", now.minusHours(1))
        coEvery { http.getFullResponse(UrlRequest(installerChecksum.downloadUrl)) } returns
            response("new-installer ${installer.name}", now.minusHours(1))
        val mapper = AdoptBinaryMapper(CachedGitHubHtmlClient(store, http))

        val binary = mapper.toBinaryList(
            listOf(archive), listOf(archive, installer, checksum, installerChecksum), emptyMap()
        ).single()

        assertEquals("new-archive", binary.`package`.checksum)
        assertEquals("new-installer", binary.installer!!.checksum)
        coVerify(exactly = 1) { http.getFullResponse(UrlRequest(checksum.downloadUrl)) }
        coVerify(exactly = 1) { http.getFullResponse(UrlRequest(installerChecksum.downloadUrl)) }
    }

    @Test
    fun `binary digests take precedence over stale metadata and checksum sidecars`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val archive = asset("OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip", now, "archive", digest("archive bytes"))
        val installer = asset(archive.name.removeSuffix(".zip") + ".msi", now, "installer", digest("installer bytes"))
        val archiveChecksum = asset("${archive.name}.sha256.txt", now)
        val installerChecksum = asset("${installer.name}.sha256.txt", now)
        val oldMetadata = UpdaterJsonMapper.mapper.readValue(metadata("stale-sidecar-checksum"), GHMetaData::class.java)
        val http = mockk<UpdaterHtmlClient>()
        val mapper = AdoptBinaryMapper(CachedGitHubHtmlClient(InMemoryInternalDbStore(), http))

        val binary = mapper.toBinaryList(
            listOf(archive), listOf(archive, installer, archiveChecksum, installerChecksum), mapOf(archive to oldMetadata)
        ).single()

        assertEquals(digest("archive bytes").removePrefix("sha256:"), binary.`package`.checksum)
        assertEquals(digest("installer bytes").removePrefix("sha256:"), binary.installer!!.checksum)
        coVerify(exactly = 0) { http.getFullResponse(any()) }
    }

    @Test
    fun `absent or malformed binary digest falls back to metadata`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val metadata = UpdaterJsonMapper.mapper.readValue(metadata("sidecar-checksum"), GHMetaData::class.java)
        val http = mockk<UpdaterHtmlClient>()
        val mapper = AdoptBinaryMapper(CachedGitHubHtmlClient(InMemoryInternalDbStore(), http))
        for (digest in listOf(null, "sha256:invalid", "sha512:unsupported")) {
            val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", now, "archive", digest)

            val binary = mapper.toBinaryList(listOf(archive), listOf(archive), mapOf(archive to metadata)).single()

            assertEquals("sidecar-checksum", binary.`package`.checksum)
        }
        coVerify(exactly = 0) { http.getFullResponse(any()) }
    }

    @Test
    fun `incremental update detects replaced binary on old EA release with unchanged metadata and timestamps`() = runBlocking {
        val oldTime = TimeSource.now().minusDays(40).withNano(0)
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", oldTime, "archive-old", digest("old bytes"))
        val metadataBody = metadata("stale-sidecar-checksum")
        val metadataAsset = asset("${archive.name}.json", oldTime, "metadata-unchanged", digest(metadataBody))
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(metadataAsset.downloadUrl)) } returns response(metadataBody, oldTime)
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val releaseTime = DateTimeFormatter.ISO_INSTANT.format(oldTime)
        val original = GHRelease(
            GitHubId("rolling"), "jdk8u512-b05-ea-beta", true, releaseTime, releaseTime,
            GHAssets(listOf(archive, metadataAsset), PageInfo(false, null), 2), "",
            "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u512-b05-ea-beta"
        )
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(mapper.toAdoptRelease(original).result!!))))
        val replacement = archive.copy(id = "archive-replaced", digest = digest("new bytes"))
        val updated = original.copy(releaseAssets = GHAssets(listOf(replacement, metadataAsset), PageInfo(false, null), 2))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before)
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        coEvery { repository.getReleaseById(GitHubId("rolling")) } coAnswers { mapper.toAdoptRelease(updated) }
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier>())

        val after = builder.incrementalUpdate(emptySet(), before) { null }

        assertEquals(digest("old bytes").removePrefix("sha256:"), before.allReleases.getReleases().single().binaries.single().`package`.checksum)
        assertEquals(digest("new bytes").removePrefix("sha256:"), after.allReleases.getReleases().single().binaries.single().`package`.checksum)
        coVerify(exactly = 1) { repository.getReleaseById(GitHubId("rolling")) }
        coVerify(exactly = 1) { http.getFullResponse(UrlRequest(metadataAsset.downloadUrl)) }
    }

    private fun cache(store: InMemoryInternalDbStore, url: String, body: String, now: ZonedDateTime) {
        store.putCachedWebpage(
            url, CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(now.minusDays(5)), now.minusHours(2), body
        )
    }

    private fun asset(name: String, updatedAt: ZonedDateTime, id: String? = null, digest: String? = null): GHAsset {
        return GHAsset(name, 1, "https://example.test/$name", 0, DateTimeFormatter.ISO_INSTANT.format(updatedAt), id, digest)
    }

    private fun digest(body: String): String {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun response(body: String, modifiedAt: ZonedDateTime): BasicHttpResponse {
        return BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK").apply {
            entity = StringEntity(body, StandardCharsets.UTF_8)
            setHeader("Last-Modified", CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(modifiedAt))
        }
    }

    private fun metadata(checksum: String): String {
        return """{
            "os": "linux", "arch": "x64", "variant": "hotspot", "binary_type": "jdk",
            "scmRef": "jdk8u512-b05", "version_data": "", "sha256": "$checksum",
            "version": {
                "major": 8, "minor": 0, "security": 512, "pre": "beta", "adopt_build_number": 0,
                "version": "1.8.0_512-beta-b05", "build": 5, "opt": "202609292016", "semver": null
            }
        }"""
    }
}
