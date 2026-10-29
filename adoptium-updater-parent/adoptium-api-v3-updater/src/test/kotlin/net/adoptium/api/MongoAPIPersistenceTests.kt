package net.adoptium.api

import com.mongodb.MongoBulkWriteException
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime

class MongoAPIPersistenceTests : MongoTest() {

    @Test
    fun `partial insert failure invalidates generation and permits recovery`(): Unit = runBlocking {
        val client = MongoClient()
        val api = MongoApiPersistence(client)
        val oldRelease = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val replacement = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.drop(1).first()
        val omitted = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val before = AdoptRepos(listOf(
            FeatureRelease(8, Releases(listOf(oldRelease))), FeatureRelease(11, Releases(listOf(omitted)))
        ))
        val target = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(replacement)))))
        val oldChecksum = V3Updater.calculateChecksum(before)
        val targetChecksum = V3Updater.calculateChecksum(target)
        api.updateAllRepos(before, oldChecksum)
        // Reject inserts only, so updateAllRepos deletes the old feature before failing.
        client.getDatabase().runCommand(Document("collMod", MongoApiPersistence.RELEASE_DB)
            .append("validator", Document("generationFailureTest", Document("\$exists", true))))
        try {
            var previousMarker = oldChecksum
            repeat(2) {
                val error = assertThrows(MongoBulkWriteException::class.java) {
                    runBlocking { api.updateAllRepos(target, targetChecksum) }
                }
                assertTrue(error.writeErrors.any { it.code == 121 }, "Propagate Mongo's document validation failure")
                val restarted = MongoApiPersistence(MongoClient())
                assertTrue(restarted.readReleaseData(8).releases.nodes.isEmpty())
                assertEquals(setOf(omitted.id), restarted.readReleaseData(11).releases.nodes.keys)
                val failedMarker = restarted.getUpdatedAt().checksum
                assertNotEquals(oldChecksum, failedMarker)
                assertNotEquals(targetChecksum, failedMarker)
                assertNotEquals(previousMarker, failedMarker, "Every failed attempt must invalidate its source generation")
                previousMarker = failedMarker
            }
        } finally {
            client.getDatabase().runCommand(Document("collMod", MongoApiPersistence.RELEASE_DB)
                .append("validator", Document()))
        }

        api.updateAllRepos(target, targetChecksum)
        val recovered = MongoApiPersistence(MongoClient())
        assertEquals(setOf(replacement.id), recovered.readReleaseData(8).releases.nodes.keys)
        assertTrue(recovered.readReleaseData(11).releases.nodes.isEmpty())
        assertEquals(targetChecksum, recovered.getUpdatedAt().checksum)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `marker failure propagates original exception without publishing target`(failOnPublish: Boolean): Unit = runBlocking {
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        val target = AdoptRepos(emptyList())
        val oldChecksum = V3Updater.calculateChecksum(before)
        val targetChecksum = V3Updater.calculateChecksum(target)
        val failure = IllegalStateException("Injected generation write failure")
        var fail = false
        val api = object : MongoApiPersistence(MongoClient()) {
            override suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
                if (fail && (checksum == targetChecksum) == failOnPublish) throw failure
                super.updateUpdatedTime(dateTime, checksum, hashCode)
            }
        }
        api.updateAllRepos(before, oldChecksum)
        fail = true
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            runBlocking { api.updateAllRepos(target, targetChecksum) }
        })
        val restarted = MongoApiPersistence(MongoClient())
        assertNotEquals(targetChecksum, restarted.getUpdatedAt().checksum)
        if (failOnPublish) {
            assertNotEquals(oldChecksum, restarted.getUpdatedAt().checksum)
            assertTrue(restarted.readReleaseData(8).releases.nodes.isEmpty())
        } else {
            // Failed invalidation must prevent all destructive release writes.
            assertEquals(oldChecksum, restarted.getUpdatedAt().checksum)
            assertEquals(setOf(release.id), restarted.readReleaseData(8).releases.nodes.keys)
        }
        fail = false
        api.updateAllRepos(target, targetChecksum)
        assertEquals(targetChecksum, restarted.getUpdatedAt().checksum)
        assertTrue(restarted.readReleaseData(8).releases.nodes.isEmpty())
    }

    @Test
    fun `complete empty snapshot removes every persisted feature`(): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val before = AdoptRepos(listOf(8, 11).map { version ->
            FeatureRelease(version, Releases(listOf(BaseTest.adoptRepos.getFeatureRelease(version)!!.releases.nodeList.first())))
        })
        api.updateAllRepos(before, V3Updater.calculateChecksum(before))
        val target = AdoptRepos(emptyList())
        val checksum = V3Updater.calculateChecksum(target)
        api.updateAllRepos(target, checksum)

        val restarted = MongoApiPersistence(MongoClient())
        for (version in 8..100) assertTrue(restarted.readReleaseData(version).releases.nodes.isEmpty())
        assertEquals(checksum, restarted.getUpdatedAt().checksum)
        assertEquals(target.hashCode(), restarted.getUpdatedAt().hashCode)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `empty and omitted features are deleted from Mongo`(omit: Boolean): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val removed = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val retained = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val before = AdoptRepos(listOf(
            FeatureRelease(8, Releases(listOf(removed))), FeatureRelease(11, Releases(listOf(retained)))
        ))
        api.updateAllRepos(before, "before-empty-feature")
        assertEquals(setOf(removed.id), api.readReleaseData(8).releases.nodes.keys)
        val after = AdoptRepos(listOf(FeatureRelease(11, Releases(listOf(retained)))) +
            if (omit) emptyList() else listOf(FeatureRelease(8, Releases(emptyList()))))
        api.updateAllRepos(after, "after-empty-feature")

        val restarted = MongoApiPersistence(MongoClient())
        assertTrue(restarted.readReleaseData(8).releases.nodes.isEmpty())
        assertEquals(setOf(retained.id), restarted.readReleaseData(11).releases.nodes.keys)
        assertEquals("after-empty-feature", restarted.getUpdatedAt().checksum)
    }

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
