package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.Vendor
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.message.BasicHttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.security.MessageDigest

class GitHubAssetCacheRefreshTest {
    private val timestamp = "2026-01-01T00:00:00Z"
    private fun asset(name: String = "metadata.json", id: String? = "asset-1", digest: String? = null) =
        GHAsset(name, 10, "https://github.com/test/$name", 1, timestamp, id, digest)
    private fun digest(bytes: ByteArray) = "sha256:" + MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun response(bytes: ByteArray, status: Int = 200) = BasicHttpResponse(org.apache.http.HttpVersion.HTTP_1_1, status, "").also {
        it.entity = ByteArrayEntity(bytes)
    }

    @Test
    fun `demand asset fetch awaits asynchronous cache write before returning`() = runBlocking {
        withTimeout(5000) {
            val store = mockk<InternalDbStore>()
            var cached: CacheDbEntry? = null
            val releaseWrite = CompletableDeferred<Unit>()
            coEvery { store.getCachedWebpage(any()) } answers { cached }
            every { store.putCachedWebpage(allAny(), any(), any(), any()) } answers {
                val entry = CacheDbEntry(firstArg(), secondArg(), thirdArg(), arg(3))
                launch {
                    releaseWrite.await()
                    cached = entry
                }
            }
            val http = mockk<UpdaterHtmlClient>()
            coEvery { http.getFullResponse(any(), any()) } answers { response("body".toByteArray()) }
            val client = CachedGitHubHtmlClient(store, http)
            val fetched = async(start = CoroutineStart.UNDISPATCHED) { client.getAsset(asset()) }
            try {
                assertFalse(fetched.isCompleted, "Demand must await its cache write")
            } finally {
                releaseWrite.complete(Unit)
            }
            assertEquals("body", fetched.await())
            assertEquals("body", client.getAsset(asset()))
            coVerify(exactly = 1) { http.getFullResponse(any(), any()) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["metadata.json", "archive.sha256.txt"])
    fun `same URL size and timestamps cannot mask asset replacement`(name: String) = runBlocking {
        val store = InMemoryInternalDbStore()
        val http = mockk<UpdaterHtmlClient>()
        var body = "old"
        coEvery { http.getFullResponse(any(), any()) } answers { response(body.toByteArray()) }
        val client = CachedGitHubHtmlClient(store, http)
        val original = asset(name)
        store.putCachedWebpage(original.downloadUrl, timestamp, TimeSource.now(), "stale URL cache")
        assertEquals("old", client.getAsset(original))
        body = "new"
        assertEquals("old", client.getAsset(original.copy(updatedAt = "2026-02-01T00:00:00Z")))
        assertEquals("new", client.getAsset(original.copy(id = "asset-2")))
        // Adding or changing a digest must not hit an ID-only or old-digest entry.
        val withDigest = original.copy(digest = digest(body.toByteArray()))
        assertEquals("new", client.getAsset(withDigest))
        body = "end"
        assertEquals("end", client.getAsset(withDigest.copy(digest = digest(body.toByteArray()))))
        assertEquals("end", client.getAsset(withDigest.copy(digest = digest(body.toByteArray()))))
        coVerify(exactly = 4) { http.getFullResponse(UrlRequest(original.downloadUrl), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["absent", "invalid", "digest-only"])
    fun `missing immutable identity fetches on demand while digest alone can cache`(mode: String) = runBlocking {
        val http = mockk<UpdaterHtmlClient>()
        val bytes = "sidecar".toByteArray()
        coEvery { http.getFullResponse(any(), any()) } answers { response(bytes) }
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        val asset = asset(id = null, digest = when (mode) {
            "invalid" -> "sha256:not-a-digest"
            "digest-only" -> digest(bytes)
            else -> null
        })
        repeat(2) { assertEquals("sidecar", client.getAsset(asset)) }
        coVerify(exactly = if (mode == "digest-only") 1 else 2) { http.getFullResponse(any(), any()) }
    }

    @Test
    fun `digest validates original bytes not a decoded and reencoded body`() = runBlocking {
        val http = mockk<UpdaterHtmlClient>()
        val bytes = "metadata café\r\n".toByteArray(Charsets.UTF_8)
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        val asset = asset(digest = digest(bytes))
        coEvery { http.getFullResponse(any(), any()) } returnsMany listOf(
            response("stale".toByteArray()), response(bytes)
        )
        assertEquals("metadata café\r\n", client.getAsset(asset))
        assertEquals("metadata café\r\n", client.getAsset(asset))
        coVerify(exactly = 2) { http.getFullResponse(any(), any()) }
    }

    @Test
    fun `transient responses retry without conditional timestamp requests`() = runBlocking {
        val http = mockk<UpdaterHtmlClient>()
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        coEvery { http.getFullResponse(any(), any()) } returnsMany listOf(null, response(byteArrayOf(), 304), response("fresh".toByteArray()))
        assertEquals("fresh", client.getAsset(asset()))
        coVerify(exactly = 3) { http.getFullResponse(UrlRequest(asset().downloadUrl), any()) }
    }

    @Test
    fun `lossy decoding cannot turn incorrect downloaded bytes into a valid digest`() = runBlocking {
        val malformed = byteArrayOf(0xc3.toByte(), 0x28)
        val decodedBytes = malformed.toString(Charsets.UTF_8).toByteArray(Charsets.UTF_8)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(any(), any()) } returnsMany listOf(response(malformed), response(decodedBytes))
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        assertEquals(decodedBytes.toString(Charsets.UTF_8), client.getAsset(asset(digest = digest(decodedBytes))))
        coVerify(exactly = 2) { http.getFullResponse(any(), any()) }
    }

    @Test
    fun `unavailable advertised checksum fails snapshot and can recover on same mapper`() = runBlocking {
        val client = mockk<GitHubHtmlClient>()
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot.tar.gz")
        val checksum = asset(archive.name + ".sha256.txt")
        val upstream = GHRelease(GitHubId("release"), "jdk8u302-b08", true, timestamp, timestamp,
            GHAssets(listOf(archive, checksum), PageInfo(false, null), 2), "", "https://github.com/adoptium/temurin8-binaries/releases/tag/ea")
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        coEvery { client.getAsset(checksum) } returns null
        val failed = mapper.toAdoptRelease(upstream)
        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        coEvery { client.getAsset(checksum) } returns "fresh-checksum filename"
        assertEquals("fresh-checksum", mapper.toAdoptRelease(upstream).result!!.single().binaries.single().`package`.checksum)
    }

    @ParameterizedTest
    @ValueSource(strings = ["sha256:short", "sha512:unsupported", "sha256:gggg", ""])
    fun `invalid binary digest falls back to metadata and installer sidecar`(invalid: String) = runBlocking {
        val client = mockk<GitHubHtmlClient>()
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot.tar.gz", digest = invalid)
        val installer = asset("OpenJDK8U-jdk_x64_linux_hotspot.msi", digest = invalid)
        val checksum = asset(installer.name + ".sha256.txt")
        coEvery { client.getAsset(checksum) } returns "installer-checksum\n"
        val result = AdoptBinaryMapper(client).toBinaryList(listOf(archive), listOf(archive, installer, checksum),
            mapOf(archive to SemeruMapperTest.data.copy(sha256 = "metadata-checksum"))).single()
        assertEquals("metadata-checksum", result.`package`.checksum)
        assertEquals("installer-checksum", result.installer!!.checksum)
        coVerify(exactly = 1) { client.getAsset(checksum) }
        coVerify(exactly = 0) { client.getUrl(any()) }
    }

    @Test
    fun `valid archive and installer digests override stale metadata and sidecars without downloading binaries`() = runBlocking {
        val client = mockk<GitHubHtmlClient>()
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot.tar.gz", digest = "sha256:" + "AB".repeat(32))
        val installer = asset("OpenJDK8U-jdk_x64_linux_hotspot.msi", digest = "sha256:" + "cd".repeat(32))
        val sidecars = listOf(archive, installer).map { asset(it.name + ".sha256.txt") }
        val result = AdoptBinaryMapper(client).toBinaryList(listOf(archive), listOf(archive, installer) + sidecars,
            mapOf(archive to SemeruMapperTest.data)).single()
        assertEquals("ab".repeat(32), result.`package`.checksum)
        assertEquals("cd".repeat(32), result.installer!!.checksum)
        coVerify(exactly = 0) { client.getAsset(any()) }
        coVerify(exactly = 0) { client.getUrl(any()) }
    }

    @Test
    fun `replaced metadata refreshes split version and failed digest never becomes cached success`() = runBlocking {
        val http = mockk<UpdaterHtmlClient>()
        val client = CachedGitHubHtmlClient(InMemoryInternalDbStore(), http)
        val mapper = AdoptReleaseMapperFactory(AdoptBinaryMapper(client), client).get(Vendor.eclipse)
        val archive = asset("OpenJDK8U-jdk_x64_linux_hotspot.tar.gz", digest = "sha256:" + "ab".repeat(32))
        var metadata = asset(archive.name + ".json")
        var data = SemeruMapperTest.data
        var body = JsonMapper.mapper.writeValueAsString(data).toByteArray()
        coEvery { http.getFullResponse(any(), any()) } answers { response(body) }
        fun upstream() = GHRelease(GitHubId("release"), "jdk8u302-b08", true, timestamp, timestamp,
            GHAssets(listOf(archive, metadata), PageInfo(false, null), 2), "", "https://github.com/adoptium/temurin8-binaries/releases/tag/ea")
        val first = mapper.toAdoptRelease(upstream()).result!!.single()
        data = data.copy(version = data.version.copy(build = 9, semver = "8.0.302+9"))
        body = JsonMapper.mapper.writeValueAsString(data).toByteArray()
        metadata = metadata.copy(id = "replacement", digest = digest(body))
        val second = mapper.toAdoptRelease(upstream()).result!!.single()
        assertEquals(9, second.version_data.build)
        assertFalse(first.id == second.id)
        assertEquals("ab".repeat(32), second.binaries.single().`package`.checksum)
        metadata = metadata.copy(id = "bad", digest = digest("different".toByteArray()))
        val failed = mapper.toAdoptRelease(upstream())
        assertFalse(failed.succeeded())
        assertTrue(failed.retryable)
        assertNull(failed.result)
        body = "different".toByteArray()
        assertEquals("different", client.getAsset(metadata))
        coVerify(exactly = 13) { http.getFullResponse(UrlRequest(metadata.downloadUrl), any()) }
        coVerify(exactly = 0) { http.getFullResponse(UrlRequest(archive.downloadUrl), any()) }
    }
}
