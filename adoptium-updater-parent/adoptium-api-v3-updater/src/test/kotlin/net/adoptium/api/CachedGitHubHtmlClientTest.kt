package net.adoptium.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import org.apache.http.HttpVersion
import org.apache.http.entity.StringEntity
import org.apache.http.message.BasicHttpResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import java.time.ZonedDateTime
import java.util.stream.Stream

@ExtendWith(MockKExtension::class)
class CachedGitHubHtmlClientTest {

    private data class TestData(
        val name: String,
        val modifiedTime: String,
        val lastChecked: ZonedDateTime,
        val expectUpdate: Boolean
    )

    private val tests = listOf(
        TestData(
            "recently modified files is not pulled within a day",
            CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(15)),
            TimeSource.now(),
            false
        ),
        TestData(
            "recently modified files is not pulled within a day 2",
            CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(15)),
            TimeSource.now().minusHours(23),
            false
        ),
        TestData(
            "recently modified files is pulled after a day",
            CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(15)),
            TimeSource.now().minusHours(26),
            true
        ),
        TestData(
            "old files are not pulled within a week",
            CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(31)),
            TimeSource.now().minusDays(6),
            false
        ),
        TestData(
            "old files are pulled after a week",
            CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(31)),
            TimeSource.now().minusDays(8),
            true
        )
    )

    @TestFactory
    fun `run CachedGitHubHtmlClient update scenarios`(): Stream<DynamicTest> {
        return tests
            .map {
                DynamicTest.dynamicTest(it.name) {
                    performRequest(it.modifiedTime, it.lastChecked, it.expectUpdate)
                }
            }
            .stream()
    }

    @Test
    fun `cacheDbEntry serialization works with absent lastChecked`() {
        val cacheDbEntry = JsonMapper.mapper.readValue(
            """
            {
                "url": "foo",
                "lastModified": "bar",
                "data": "some data"
            }
            """.trimIndent(),
            CacheDbEntry::class.java
        )

        assertEquals("foo", cacheDbEntry.url)
        assertNull(cacheDbEntry.lastChecked)
    }

    @Test
    fun `background HTTP 200 updates content without recording demand usage`() = runBlocking {
        val memory = InMemoryInternalDbStore()
        val modified = CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(TimeSource.now().minusDays(20))
        memory.putCachedWebpage("background", modified, TimeSource.now().minusDays(2), "old")
        val written = CompletableDeferred<ZonedDateTime?>()
        val store = recordingStore(memory, written)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest("background", modified)) } returns response("new")
        val client = CachedGitHubHtmlClient(store, http)

        assertEquals("old", client.getUrl("background"))
        assertNull(withTimeout(5000) { written.await() })
        assertEquals("new", memory.getCachedWebpage("background")!!.data)
    }

    @Test
    fun `direct unversioned asset fetch records demand usage without a cache read`() = runBlocking {
        val memory = InMemoryInternalDbStore()
        val now = TimeSource.now()
        memory.putCachedWebpage("direct", null, now.minusDays(20), "old")
        val written = CompletableDeferred<ZonedDateTime?>()
        val store = recordingStore(memory, written)
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest("direct")) } returns response("new")
        val client = CachedGitHubHtmlClient(store, http)
        val asset = GHAsset("metadata.json", 1, "direct", 0, "2013-02-27T19:35:32Z")

        assertEquals("new", client.getAsset(asset))
        assertNotNull(withTimeout(5000) { written.await() })
        assertEquals(0L, memory.evictUnusedCache(now.plusDays(29)))
    }

    @Test
    fun `uncached demand fetch initializes usage`() = runBlocking {
        val store = InMemoryInternalDbStore()
        val http = mockk<UpdaterHtmlClient>()
        coEvery { http.getFullResponse(UrlRequest("demand")) } returns response("body")
        val before = TimeSource.now()
        assertEquals("body", CachedGitHubHtmlClient(store, http).getUrl("demand"))
        assertEquals(0L, store.evictUnusedCache(before))
        assertTrue(!store.getCachedWebpage("demand")!!.lastAccessed!!.isBefore(before))
    }

    @TestFactory
    fun `background validation does not prolong demand usage`(): Stream<DynamicTest> = listOf(200, 304).map { status ->
        DynamicTest.dynamicTest("HTTP $status preserves usage") {
            runBlocking {
                val backing = InMemoryInternalDbStore()
                val now = TimeSource.now()
                val modified = CachedGitHubHtmlClient.LAST_MODIFIED_PARSER.format(now)
                backing.putCachedWebpage("background", modified, now.minusDays(2), "old", now.minusDays(31)).join()
                val releaseResponse = CompletableDeferred<Unit>()
                val validated = CompletableDeferred<Unit>()
                var demandUsage: ZonedDateTime? = null
                val store = object : InternalDbStore by backing {
                    override suspend fun getCachedWebpage(url: String): CacheDbEntry? =
                        backing.getCachedWebpage(url)?.also { demandUsage = it.lastAccessed }

                    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Job {
                        assertNull(accessedAt, "Background HTTP 200 must not be a demand write")
                        return backing.putCachedWebpage(url, lastModified, date, data, accessedAt)
                            .also { validated.complete(Unit) }
                    }

                    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
                        backing.updateCheckedTime(url, dateTime)
                        validated.complete(Unit)
                    }
                }
                val http = mockk<UpdaterHtmlClient>()
                coEvery { http.getFullResponse(UrlRequest("background", modified)) } coAnswers {
                    releaseResponse.await()
                    BasicHttpResponse(HttpVersion.HTTP_1_1, status, "OK").apply { entity = StringEntity("new") }
                }
                assertEquals("old", CachedGitHubHtmlClient(store, http).getUrl("background"))
                assertTrue(!demandUsage!!.isBefore(now), "Cache hits are demand reads")
                releaseResponse.complete(Unit)
                withTimeout(5000) { validated.await() }
                assertEquals(1L, backing.evictUnusedCache(demandUsage!!.plusDays(30)))
            }
        }
    }.stream()

    private fun recordingStore(memory: InternalDbStore, written: CompletableDeferred<ZonedDateTime?>): InternalDbStore {
        return object : InternalDbStore by memory {
            override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Job {
                return memory.putCachedWebpage(url, lastModified, date, data, accessedAt).also {
                    it.invokeOnCompletion { written.complete(accessedAt) }
                }
            }
        }
    }

    private fun response(body: String) = BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK").apply {
        entity = StringEntity(body)
    }

    private fun performRequest(modifiedTime: String?, lastChecked: ZonedDateTime, expectUpdate: Boolean) {
        runBlocking {
            val internalDbStore = InMemoryInternalDbStore()
            internalDbStore.putCachedWebpage("foo", modifiedTime, lastChecked, "bar")

            val updaterHtmlClient: UpdaterHtmlClient = mockk()
            coEvery { updaterHtmlClient.get("foo") }.returns("bar")
            val client = CachedGitHubHtmlClient(internalDbStore, updaterHtmlClient)

            client.getUrl("foo")
            val request = UrlRequest("foo", modifiedTime)

            if (expectUpdate) {
                coVerify(exactly = 1, timeout = 3000) {
                    updaterHtmlClient.getFullResponse(request)
                }
            } else {
                while (client.getQueueLength() > 0) {
                    delay(1000)
                }

                coVerify(timeout = 3000, exactly = 0) {
                    updaterHtmlClient.getFullResponse(request)
                }

                confirmVerified(updaterHtmlClient)
            }
        }
    }
}
