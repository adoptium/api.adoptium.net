package net.adoptium.api.v3.stats.cloudflare

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class CloudflareClientTest {

    @Test
    fun `should successfully fetch and aggregate paginated results`() = runBlocking {
        val testDate = LocalDate.of(2024, 1, 15)
        val response = CloudflareResponse(
            data = setOf(
                CloudflarePackageStats(testDate, 100, "/artifactory/deb/pool/main/t/temurin-17/temurin-17-jdk_17.0.10_amd64.deb"),
                CloudflarePackageStats(testDate, 50, "/artifactory/rpm/centos/7/x86_64/Packages/temurin-17-jdk-17.0.10.x86_64.rpm")
            )
        )

        val mockClient = mockk<CloudflareClient>() {
            coEvery { fetchDownloadStats(any(), any()) } returns response
        }
        val database = InMemoryApiPersistence(AdoptRepos(emptyList()), AdoptAttestationRepos(emptyList()))
        val calculator = CloudflareStatsCalculator(database, mockClient)

        calculator.updateDb()

        val startTime = testDate.atStartOfDay(ZoneId.of("UTC")).minusDays(1)
        val endTime = testDate.plusDays(1).atStartOfDay(ZoneId.of("UTC"))
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
        val date = LocalDate.of(2024, 1, 15)
        val response1 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(date, 50, "/path/duplicated.deb"),
                CloudflarePackageStats(date, 100, "/path/one.deb"))
        )
        val response2 = CloudflareResponse(
            setOf(
                CloudflarePackageStats(date, 50, "/path/duplicated.deb"),
                CloudflarePackageStats(date, 200, "/path/two.deb")
            )
        )

        val merged = response1.merge(response2)

        assertEquals(3, merged.data.size)
        assertTrue(merged.data.any { it.path == "/path/one.deb" && it.count == 100L })
        assertTrue(merged.data.any { it.path == "/path/two.deb" && it.count == 200L })
        assertTrue(merged.data.any { it.path == "/path/duplicated.deb" && it.count == 50L })
    }
}
