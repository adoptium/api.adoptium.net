package net.adoptopenjdk.api

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryInternalDbStore
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.mongo.CacheDbEntry
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import java.time.ZonedDateTime
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                coVerify(timeout = 3000) {
                    updaterHtmlClient.getFullResponse(request)?.wasNot(Called)
                }
                confirmVerified(updaterHtmlClient)
            }
        }
    }
}
