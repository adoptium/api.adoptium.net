package net.adoptium.api.v3.stats.cloudflare

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class CloudflareClientTest {

    private companion object {
        private val ZONE_ID_UTC = ZoneId.of("UTC")
    }

    @Test
    fun `should successfully fetch and aggregate paginated results`() = runBlocking {
        val testDateTime = Instant.parse("2024-01-15T00:00:00Z")
        val response = CloudflareResponse(
            data = setOf(
                CloudflarePackageStats(testDateTime, 100, "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.10_amd64.deb"),
                CloudflarePackageStats(testDateTime, 50, "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.10.x86_64.rpm")
            )
        )

        val mockClient = mockk<CloudflareClient>() {
            coEvery { fetchDownloadStats(any(), any()) } returns response
        }
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptCdxaRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        calculator.updateDb()

        val startTime = testDateTime.minus(1, ChronoUnit.DAYS).atZone(ZONE_ID_UTC)
        val endTime = testDateTime.plus(1, ChronoUnit.DAYS).atZone(ZONE_ID_UTC)
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
        val datetime = Instant.parse("2024-01-15T00:00:00Z")
        val response1 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(datetime, 50, "/path/duplicated.deb"),
                CloudflarePackageStats(datetime, 100, "/path/one.deb"))
        )
        val response2 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(datetime, 50, "/path/duplicated.deb"),
                CloudflarePackageStats(datetime, 200, "/path/two.deb")
            )
        )

        val merged = response1.merge(response2)

        assertEquals(3, merged.data.size)
        assertTrue(merged.data.any { it.path == "/path/one.deb" && it.count == 100L })
        assertTrue(merged.data.any { it.path == "/path/two.deb" && it.count == 200L })
        assertTrue(merged.data.any { it.path == "/path/duplicated.deb" && it.count == 50L })
    }
}
