package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.HexFormat

class GitHubAssetCacheRefreshTest {

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
    fun `matching binary digests metadata and checksum sidecars are published`() = runBlocking {
        val now = TimeSource.now().withNano(0)
        val archive = asset("OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip", now, "archive", digest("archive bytes"))
        val installer = asset(archive.name.removeSuffix(".zip") + ".msi", now, "installer", digest("installer bytes"))
        val archiveChecksum = asset("${archive.name}.sha256.txt", now)
        val installerChecksum = asset("${installer.name}.sha256.txt", now)
        val archiveHash = archive.digest!!.removePrefix("sha256:")
        val installerHash = installer.digest!!.removePrefix("sha256:")
        val currentMetadata = UpdaterJsonMapper.mapper.readValue(metadata(archiveHash), GHMetaData::class.java)
        val installerMetadataBody = metadata(installerHash)
        val installerMetadata = asset("${installer.name}.json", now, "installer-metadata", digest(installerMetadataBody))
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(archiveChecksum.downloadUrl)) } returns
            response("$archiveHash ${archive.name}", now)
        coEvery { http.getFullResponse(UrlRequest(installerChecksum.downloadUrl)) } returns
            response("$installerHash ${installer.name}", now)
        coEvery { http.getFullResponse(UrlRequest(installerMetadata.downloadUrl)) } returns response(installerMetadataBody, now)
        val mapper = AdoptBinaryMapper(CachedGitHubHtmlClient(InMemoryInternalDbStore(), http))

        val binary = mapper.toBinaryList(
            listOf(archive), listOf(archive, installer, archiveChecksum, installerChecksum, installerMetadata), mapOf(archive to currentMetadata)
        ).single()

        assertEquals(digest("archive bytes").removePrefix("sha256:"), binary.`package`.checksum)
        assertEquals(digest("installer bytes").removePrefix("sha256:"), binary.installer!!.checksum)
        assertEquals(installerMetadata.downloadUrl, binary.installer!!.metadata_link)
        coVerify(exactly = 3) { http.getFullResponse(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["metadata", "sidecar", "legacy", "split", "all", "mixed-failure"])
    fun `checksum disagreements exclude only affected binaries and can recover`(scenario: String) = runBlocking {
        val now = TimeSource.now().minusDays(1)
        val hash = "a".repeat(64)
        val otherHash = "b".repeat(64)
        val good = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", now, "good", "sha256:$hash")
            .copy(downloadCount = 5)
        val bad = asset("OpenJDK8U-jdk_aarch64_linux_hotspot_8u512b05-ea.tar.gz", now, "bad", "sha256:$hash")
            .copy(downloadCount = 7)
        val binaries = listOf(good, bad)
        val client = mockk<GitHubHtmlClient>()
        val sidecars = binaries.map { asset("${it.name}.sha256.txt", now) }
        val metadataAssets = if (scenario == "legacy") emptyList() else binaries.map { asset("${it.name}.json", now) }
        fun metadataBody(binary: GHAsset, checksum: String): String {
            val body = metadata(checksum)
            return if (scenario == "split" && binary == bad) body.replace("\"build\": 5", "\"build\": 6") else body
        }
        for ((index, binary) in binaries.withIndex()) {
            val mismatch = binary == bad || scenario == "all"
            val sidecarHash = if (mismatch && scenario in setOf("sidecar", "legacy")) otherHash else hash
            coEvery { client.getAsset(sidecars[index]) } returns "$sidecarHash  ${binary.name}\n"
            if (metadataAssets.isNotEmpty()) {
                val metadataHash = if (mismatch && scenario !in setOf("sidecar", "legacy")) otherHash else hash
                coEvery { client.getAsset(metadataAssets[index]) } returns metadataBody(binary, metadataHash)
            }
        }
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val releaseTime = DateTimeFormatter.ISO_INSTANT.format(now)
        val upstream = GHRelease(
            GitHubId("checksums"), "jdk8u512-b05-ea-beta", true, releaseTime, releaseTime,
            GHAssets(binaries.map {
                if (scenario == "mixed-failure" && it == good) it.copy(updatedAt = "invalid-date") else it
            } + sidecars + metadataAssets, PageInfo(false, null), 0), "",
            "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u512-b05-ea-beta"
        )

        val excluded = mapper.toAdoptRelease(upstream)

        if (scenario == "mixed-failure") {
            assertFalse(excluded.succeeded())
            assertTrue(excluded.retryable)
            assertNull(excluded.result)
            return@runBlocking
        }
        assertTrue(excluded.succeeded())
        assertFalse(excluded.retryable)
        assertEquals(
            if (scenario == "all") emptyList() else listOf(good.name),
            excluded.result!!.flatMap { it.binaries.toList() }.map { it.`package`.name }
        )
        assertEquals(12L, excluded.result!!.sumOf { it.download_count })
        assertEquals(if (scenario == "split") 2 else 1, excluded.result!!.size)
        for ((index, binary) in binaries.withIndex()) {
            coEvery { client.getAsset(sidecars[index]) } returns "$hash ${binary.name}"
            if (metadataAssets.isNotEmpty()) {
                coEvery { client.getAsset(metadataAssets[index]) } returns metadataBody(binary, hash)
            }
        }
        val recovered = mapper.toAdoptRelease(upstream)
        assertTrue(recovered.succeeded())
        assertEquals(binaries.map { it.name }.toSet(), recovered.result!!.flatMap { it.binaries.toList() }.map { it.`package`.name }.toSet())
    }

    @Test
    fun `installer checksum disagreement excludes only the installer`() = runBlocking {
        val now = TimeSource.now()
        val hash = "a".repeat(64)
        val archive = asset("OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip", now, "archive", "sha256:$hash")
        val installer = asset(archive.name.removeSuffix(".zip") + ".msi", now, "installer", "sha256:$hash")
        val checksum = asset("${installer.name}.sha256.txt", now)
        val client = mockk<GitHubHtmlClient>()
        coEvery { client.getAsset(checksum) } returns "${"b".repeat(64)} ${installer.name}"

        val binary = AdoptBinaryMapper(client).toBinaryList(
            listOf(archive), listOf(archive, installer, checksum), emptyMap()
        ).single()

        assertEquals(hash, binary.`package`.checksum)
        assertNull(binary.installer)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "msi-matching", "pkg-matching", "msi-mismatch", "pkg-mismatch",
        "msi-no-sidecar", "pkg-no-sidecar", "msi-absent", "pkg-absent",
        "msi-unavailable", "pkg-invalid", "msi-exception", "pkg-fallback", "msi-exact"
    ])
    fun `installer asset metadata participates in checksum verification without changing archive mapping`(scenario: String) = runBlocking {
        val (extension, outcome) = scenario.split("-", limit = 2)
        val now = TimeSource.now().minusDays(1)
        val archiveHash = "a".repeat(64)
        val installerHash = "b".repeat(64)
        val archiveName = if (extension == "msi") {
            "OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip"
        } else {
            "OpenJDK8U-jdk_x64_mac_hotspot_8u512b05-ea.tar.gz"
        }
        val archive = asset(archiveName, now, "archive", "sha256:$archiveHash")
        val installer = asset(archiveName.substringBefore(".") + ".$extension", now, "installer",
            if (outcome == "fallback") null else "sha256:$installerHash")
        val archiveMetadata = asset("${archive.name}.json", now, "archive-metadata")
        val installerMetadata = asset("${installer.name}.json", now, "installer-metadata")
        val checksum = asset("${installer.name}.sha256.txt", now)
        val genericMetadata = asset("${installer.name.substringBefore(".")}.json", now)
        val client = mockk<GitHubHtmlClient>()
        coEvery { client.getAsset(archiveMetadata) } returns metadata(archiveHash)
        var metadataHash = if (outcome in setOf("mismatch", "no-sidecar")) "c".repeat(64) else installerHash
        var metadataFailure = outcome
        coEvery { client.getAsset(installerMetadata) } answers {
            when (metadataFailure) {
                "unavailable" -> null
                "invalid" -> "not JSON"
                "exception" -> throw IllegalStateException("Installer metadata retrieval failed")
                else -> metadata(metadataHash).replace("\"build\": 5", "\"build\": 99")
            }
        }
        coEvery { client.getAsset(checksum) } returns "$installerHash ${installer.name}"
        coEvery { client.getAsset(genericMetadata) } returns metadata(archiveHash)
        val metadataAssets = (if (outcome == "exact") listOf(genericMetadata) else emptyList()) +
            listOf(archiveMetadata) + if (outcome == "absent") emptyList() else listOf(installerMetadata)
        val checksumAssets = if (outcome in setOf("no-sidecar", "fallback")) emptyList() else listOf(checksum)
        val time = DateTimeFormatter.ISO_INSTANT.format(now)
        val upstream = GHRelease(
            GitHubId("installer"), "jdk8u512-b05-ea-beta", true, time, time,
            GHAssets(listOf(archive, installer) + metadataAssets + checksumAssets, PageInfo(false, null), 0),
            "", "https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u512-b05-ea-beta"
        )
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)

        val result = mapper.toAdoptRelease(upstream)

        assertTrue(result.succeeded())
        assertFalse(result.retryable)
        val release = result.result!!.single()
        assertEquals(5, release.version_data.build)
        val binary = release.binaries.single()
        assertEquals(archiveHash, binary.`package`.checksum)
        val excluded = outcome in setOf("mismatch", "no-sidecar", "unavailable", "invalid", "exception")
        if (excluded) {
            assertNull(binary.installer)
            metadataHash = installerHash
            metadataFailure = "matching"
            val recovered = mapper.toAdoptRelease(upstream)
            assertTrue(recovered.succeeded())
            assertEquals(installerHash, recovered.result!!.single().binaries.single().installer!!.checksum)
        } else {
            assertEquals(installerHash, binary.installer!!.checksum)
            assertEquals(if (outcome == "absent") null else installerMetadata.downloadUrl, binary.installer!!.metadata_link)
        }
        coVerify(exactly = if (outcome == "absent") 0 else if (excluded) 2 else 1) {
            client.getAsset(installerMetadata)
        }
        coVerify(exactly = 0) { client.getAsset(genericMetadata) }
    }

    @Test
    fun `installer metadata cancellation propagates instead of publishing an unchecked installer`() {
        val now = TimeSource.now()
        val archive = asset("OpenJDK8U-jdk_x64_windows_hotspot_8u512b05-ea.zip", now)
        val installer = asset(archive.name.removeSuffix(".zip") + ".msi", now)
        val metadata = asset("${installer.name}.json", now)
        val client = mockk<GitHubHtmlClient>()
        coEvery { client.getAsset(metadata) } throws CancellationException("Cancelled installer metadata")

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                AdoptBinaryMapper(client).toBinaryList(listOf(archive), listOf(archive, installer, metadata), emptyMap())
            }
        }

        assertEquals("Cancelled installer metadata", thrown.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["hash", "filename", "tabs"])
    fun `checksum comparison accepts case and common sidecar formats`(format: String) = runBlocking {
        val now = TimeSource.now()
        val hash = "a".repeat(64)
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", now, "archive", "sha256:$hash")
        val checksum = asset("${archive.name}.sha256.txt", now)
        val client = mockk<GitHubHtmlClient>()
        coEvery { client.getAsset(checksum) } returns when (format) {
            "hash" -> "${hash.uppercase()}\n"
            "tabs" -> "${hash.uppercase()}\t${archive.name}\n"
            else -> "${hash.uppercase()}  *${archive.name}\n"
        }
        val binary = AdoptBinaryMapper(client).toBinaryList(listOf(archive), listOf(archive, checksum), emptyMap()).single()
        assertEquals(hash, binary.`package`.checksum)
    }

    @Test
    fun `unavailable optional checksum sidecar does not exclude a binary`() = runBlocking {
        val now = TimeSource.now()
        val hash = "a".repeat(64)
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", now, "archive", "sha256:$hash")
        val checksum = asset("${archive.name}.sha256.txt", now)
        val client = mockk<GitHubHtmlClient>()
        coEvery { client.getAsset(checksum) } returns null

        val binary = AdoptBinaryMapper(client).toBinaryList(listOf(archive), listOf(archive, checksum), emptyMap()).single()

        assertEquals(hash, binary.`package`.checksum)
        coVerify(exactly = 1) { client.getAsset(checksum) }
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
    fun `incremental update excludes a replaced binary with stale metadata and recovers`() = runBlocking {
        val oldTime = TimeSource.now().minusDays(40).withNano(0)
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot_8u512b05-ea.tar.gz", oldTime, "archive-old", digest("old bytes"))
        val metadataBody = metadata(archive.digest!!.removePrefix("sha256:"))
        val replacementMetadataBody = metadata(digest("new bytes").removePrefix("sha256:"))
        val metadataAsset = asset("${archive.name}.json", oldTime, "metadata-unchanged", digest(metadataBody))
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest(metadataAsset.downloadUrl)) } returnsMany listOf(
            response(metadataBody, oldTime), response(replacementMetadataBody, oldTime)
        )
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
        var updated = original.copy(releaseAssets = GHAssets(listOf(replacement, metadataAsset), PageInfo(false, null), 2))
        val summary = GHSummaryTestDataGenerator.generateGHRepositorySummary(
            GHSummaryTestDataGenerator.generateGHRepository(before)
        )
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(8) } returns summary
        coEvery { repository.getReleaseById(GitHubId("rolling")) } coAnswers { mapper.toAdoptRelease(updated) }
        val builder = AdoptReposBuilder(repository, mockk<VersionSupplier> {
            every { getAllVersions() } returns arrayOf(8)
        })

        val after = builder.incrementalUpdate(emptySet(), before) { null }

        assertEquals(digest("old bytes").removePrefix("sha256:"), before.allReleases.getReleases().single().binaries.single().`package`.checksum)
        assertTrue(after.allReleases.getReleases().single().binaries.isEmpty())
        coVerify(exactly = 1) { repository.getReleaseById(GitHubId("rolling")) }
        coVerify(exactly = 1) { http.getFullResponse(UrlRequest(metadataAsset.downloadUrl)) }

        val replacementMetadata = metadataAsset.copy(id = "metadata-replaced", digest = digest(replacementMetadataBody))
        updated = updated.copy(releaseAssets = GHAssets(listOf(replacement, replacementMetadata), PageInfo(false, null), 2))
        val recovered = builder.incrementalUpdate(emptySet(), after) { null }
        assertEquals(digest("new bytes").removePrefix("sha256:"), recovered.allReleases.getReleases().single().binaries.single().`package`.checksum)
        coVerify(exactly = 2) { repository.getReleaseById(GitHubId("rolling")) }
        coVerify(exactly = 2) { http.getFullResponse(UrlRequest(metadataAsset.downloadUrl)) }
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
