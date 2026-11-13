package net.adoptium.api.v3.stats.cloudflare

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.concurrent.FutureCallback
import org.apache.http.nio.client.HttpAsyncClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

class CloudflareClientTest {

    @Test
    fun `should successfully fetch and aggregate paginated results`() = runBlocking {
        val response = CloudflareResponse(
            data = setOf(
                CloudflarePackageStats(100, "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.10_amd64.deb"),
                CloudflarePackageStats(50, "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.10.x86_64.rpm")
            )
        )

        val mockClient = mockk<CloudflareClient>() {
            coEvery { fetchDownloadStats(any(), any()) } returns response
        }
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptCdxaRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        calculator.updateDb()

        val startTime = Instant.EPOCH.atZone(TimeSource.ZONE)
        val endTime = Instant.now().plus(1, ChronoUnit.DAYS).atZone(TimeSource.ZONE)
        val savedStats = database.getPackageStats(startTime, endTime)

        assertEquals(1, savedStats.size)
        assertEquals(150, savedStats.first().downloads)
    }

    @Test
    fun `GraphQLError data class should hold error information`() {
        val error = GraphQLError(
            message = "test error",
            path = listOf("viewer", "zones"),
            timestamp = "2024-01-15T10:00:00Z"
        )

        assertEquals("test error", error.message)
        assertEquals(listOf("viewer", "zones"), error.path)
        assertEquals("2024-01-15T10:00:00Z", error.timestamp)
    }


    @Test
    fun `CloudflareResponse merge should keep unique entries separate and keep latest copy`() {
        val response1 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(50, "/path/duplicated.deb"),
                CloudflarePackageStats(100, "/path/one.deb"))
        )
        val response2 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(50, "/path/duplicated.deb"),
                CloudflarePackageStats(200, "/path/two.deb")
            )
        )

        val merged = response1.merge(response2)

        assertEquals(3, merged.data.size)
        assertTrue(merged.data.any { it.path == "/path/one.deb" && it.count == 100L })
        assertTrue(merged.data.any { it.path == "/path/two.deb" && it.count == 200L })
        assertTrue(merged.data.any { it.path == "/path/duplicated.deb" && it.count == 50L })
    }

    @Test
    fun `cancelling fetch should cancel in-flight HTTP request`() = runBlocking {
        val previousToken = APIConfig.ENVIRONMENT.put("CLOUDFLARE_API_TOKEN", "test-token")
        val previousZoneTag = APIConfig.ENVIRONMENT.put("CLOUDFLARE_ZONE_TAG", "test-zone")

        try {
            val requestFuture = CompletableFuture<HttpResponse>()
            val httpClient = mockk<HttpAsyncClient>() {
                every {
                    execute(any<HttpPost>(), any<FutureCallback<HttpResponse>>())
                } returns requestFuture
            }
            val client = CloudflareClient(httpClient)

            val fetchJob = launch(start = CoroutineStart.UNDISPATCHED) {
                client.fetchDownloadStats(Instant.EPOCH.atZone(TimeSource.ZONE), Instant.now().atZone(TimeSource.ZONE))
            }

            assertFalse(requestFuture.isCancelled)
            fetchJob.cancelAndJoin()

            assertTrue(requestFuture.isCancelled)
        } finally {
            if (previousToken == null) {
                APIConfig.ENVIRONMENT.remove("CLOUDFLARE_API_TOKEN")
            } else {
                APIConfig.ENVIRONMENT["CLOUDFLARE_API_TOKEN"] = previousToken
            }
            if (previousZoneTag == null) {
                APIConfig.ENVIRONMENT.remove("CLOUDFLARE_ZONE_TAG")
            } else {
                APIConfig.ENVIRONMENT["CLOUDFLARE_ZONE_TAG"] = previousZoneTag
            }
        }
    }
}
