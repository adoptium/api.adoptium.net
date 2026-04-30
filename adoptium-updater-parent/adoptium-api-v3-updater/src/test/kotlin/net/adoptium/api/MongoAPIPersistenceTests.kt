package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoAPIPersistenceTests : MongoTest() {

    @Test
    fun `update time is set`(api: MongoApiPersistence) {
        runBlocking {
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            val time = TimeSource.now()
            api.updateUpdatedTime(time, "", 0)

            val stored = api.getUpdatedAt()

            assertEquals(time, stored.time)
        }
    }

    @Test
    fun `cdxa update time is set`(api: MongoApiPersistence) {
        runBlocking {
            api.updateCdxaUpdatedTime(TimeSource.now(), "", 0)
            api.updateCdxaUpdatedTime(TimeSource.now(), "", 0)
            api.updateCdxaUpdatedTime(TimeSource.now(), "", 0)
            val time = TimeSource.now()
            api.updateCdxaUpdatedTime(time, "", 0)

            val stored = api.getCdxaUpdatedAt()

            assertEquals(time, stored.time)
        }
    }

    @Test
    fun `metadata is persisted`() {
        runBlocking {
            val apiPersistence = MongoApiPersistence(MongoClient())

            val metadata = GHReleaseMetadata(10, GitHubId("foo"))
            apiPersistence.setGhReleaseMetadata(metadata)

            val saved = apiPersistence.getGhReleaseMetadata(GitHubId("foo"))


            assertEquals(metadata, saved)
        }
    }

    @Test
    fun `getAggregatedPackageStats should sum downloads and return max date per feature version`() {
        runBlocking {
            val apiPersistence = MongoApiPersistence(MongoClient())
            val baseTime = TimeSource.now()

            // Clean up any leftover data from previous tests
            apiPersistence.removeStatsBetween(baseTime.minusDays(1), baseTime.plusDays(1))

            apiPersistence.addPackageDownloadStatsEntries(
                listOf(
                    CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(3), 400, 17),
                    CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(2), 350, 17),
                    CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 250, 17),
                    CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(2), 300, 21),
                    CloudflarePackageDownloadStatsDbEntry(baseTime.minusMinutes(1), 200, 21)
                )
            )

            val start = baseTime.minusDays(1)
            val end = baseTime.plusDays(1)

            val aggregated = apiPersistence.getAggregatedPackageStats(start, end)
                .sortedBy { it.feature_version }

            assertEquals(2, aggregated.size)

            val v17 = aggregated.find { it.feature_version == 17 }!!
            assertEquals(1000L, v17.downloads)
            assertEquals(baseTime.minusMinutes(1), v17.date)

            val v21 = aggregated.find { it.feature_version == 21 }!!
            assertEquals(500L, v21.downloads)
            assertEquals(baseTime.minusMinutes(1), v21.date)
        }
    }

    @Test
    fun `getAggregatedPackageStats should return empty list when no data matches date range`() {
        runBlocking {
            val apiPersistence = MongoApiPersistence(MongoClient())
            val baseTime = TimeSource.now()

            // Use a date far in the future to avoid colliding with other tests
            val farFuture = baseTime.plusYears(10)

            apiPersistence.addPackageDownloadStatsEntries(
                listOf(
                    CloudflarePackageDownloadStatsDbEntry(farFuture, 100, 17)
                )
            )

            // Query a range that doesn't overlap with the data
            val start = baseTime.minusDays(30)
            val end = baseTime.minusDays(10)

            val aggregated = apiPersistence.getAggregatedPackageStats(start, end)

            assertEquals(0, aggregated.size)
        }
    }
}
