package net.adoptium.api

import com.mongodb.MongoBulkWriteException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.models.ReleaseNote
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.models.Vendor
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

    @ParameterizedTest
    @ValueSource(strings = ["frontend", "updater"])
    fun `same-second identical republication invalidates an overlapping snapshot read`(reader: String): Unit = runBlocking {
        val time = TimeSource.now()
        val api = object : MongoApiPersistence(MongoClient()) {
            override suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
                super.updateUpdatedTime(time, checksum, hashCode)
            }
        }
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val repo = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        val checksum = V3Updater.calculateChecksum(repo)
        api.updateAllRepos(repo, checksum)
        val before = api.getUpdatedAt()
        val overlapping = object : ApiPersistence by api {
            override suspend fun readReleaseData(featureVersion: Int): FeatureRelease {
                api.updateAllRepos(repo, checksum)
                return FeatureRelease(featureVersion, emptyList())
            }

            override suspend fun readAllReleaseData(): AdoptRepos {
                api.updateAllRepos(repo, checksum)
                return AdoptRepos(emptyList())
            }
        }

        assertThrows(IllegalStateException::class.java) {
            if (reader == "frontend") APIDataStoreImpl.loadDataFromDb(overlapping, before, true, repo, listOf(8), false)
            else APIDataStoreImpl.loadUpdaterSnapshot(overlapping)
        }

        val after = MongoApiPersistence(MongoClient()).getUpdatedAt()
        assertEquals(time, before.time)
        assertEquals(before.time, after.time)
        assertEquals(before.checksum, after.checksum)
        assertEquals(before.hashCode, after.hashCode)
        assertNotEquals(before, after)
        assertEquals(repo.getFeatureRelease(8)!!.releases.nodes,
            APIDataStoreImpl.loadUpdaterSnapshot(api).getFeatureRelease(8)!!.releases.nodes)
    }

    @Test
    fun `republication replaces releases by id without duplicates or an unusable window`(): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val all = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.take(3)
        api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(8, Releases(all)))), "first")
        val kept = all.first()

        api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(kept, kept))))), "second")

        assertEquals(listOf(kept.id), api.readReleaseData(8).releases.nodeList.map { it.id })
    }

    @Test
    fun `checksum of a reloaded snapshot does not depend on persisted release order`(): Unit = runBlocking {
        val client = MongoClient()
        val api = MongoApiPersistence(client)
        client.getDatabase().getCollection<Document>(MongoApiPersistence.RELEASE_DB).deleteMany(Document())
        val (first, second) = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.take(2)
        val other = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(second)))))
        api.updateAllRepos(before, V3Updater.calculateChecksum(before))
        // The upsert appends the earlier release after the stored one, and features are out of order
        val target = AdoptRepos(listOf(
            FeatureRelease(11, Releases(listOf(other))), FeatureRelease(8, Releases(listOf(first, second)))
        ))
        val checksum = V3Updater.calculateChecksum(target)
        api.updateAllRepos(target, checksum)

        val reloaded = APIDataStoreImpl.loadUpdaterSnapshot(MongoApiPersistence(MongoClient()))
        assertNotEquals(target.repos.keys.toList(), reloaded.repos.keys.toList())
        assertNotEquals(
            target.getFeatureRelease(8)!!.releases.nodes.keys.toList(),
            reloaded.getFeatureRelease(8)!!.releases.nodes.keys.toList()
        )
        assertEquals(target, reloaded)
        assertEquals(checksum, V3Updater.calculateChecksum(reloaded))
        assertEquals(checksum, api.getUpdatedAt().checksum)
    }

    @Test
    fun `conflicting persisted release copies are deduplicated before enforcing a unique id`(): Unit = runBlocking {
        val client = MongoClient()
        MongoApiPersistence(client)
        val collection = client.getDatabase().getCollection<Document>(MongoApiPersistence.RELEASE_DB)
        collection.deleteMany(Document())
        val index = collection.listIndexes<Document>().toList().single { it["key"] == Document("id", 1) }
        collection.dropIndex(index.getString("name"))
        collection.createIndex(Document("id", 1))
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first { it.binaries.isNotEmpty() }
        val conflicting = Release(release, emptyArray())
        client.getDatabase().getCollection<Release>(MongoApiPersistence.RELEASE_DB)
            .insertMany(listOf(conflicting, release, conflicting))

        val api = MongoApiPersistence(MongoClient())
        assertEquals(1, collection.countDocuments(Document("id", release.id)))
        assertTrue(collection.listIndexes<Document>().toList().single {
            it["key"] == Document("id", 1)
        }.getBoolean("unique"))
        assertThrows(com.mongodb.MongoWriteException::class.java) {
            runBlocking { client.getDatabase().getCollection<Release>(MongoApiPersistence.RELEASE_DB).insertOne(conflicting) }
        }

        val target = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        api.updateAllRepos(target, V3Updater.calculateChecksum(target))
        assertEquals(target, APIDataStoreImpl.loadUpdaterSnapshot(MongoApiPersistence(MongoClient())))
    }

    @Test
    fun `a publication interrupted before the final generation write leaves every release readable`(): Unit = runBlocking {
        val failure = IllegalStateException("interrupted")
        val api = object : MongoApiPersistence(MongoClient()) {
            var fail = false
            override suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
                if (fail) throw failure
                super.updateUpdatedTime(dateTime, checksum, hashCode)
            }
        }
        val all = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.take(3)
        api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(8, Releases(all.take(2))))), "first")
        val published = api.getUpdatedAt()
        api.fail = true

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            runBlocking { api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(8, Releases(all.drop(1))))), "second") }
        })

        assertEquals(published, api.getUpdatedAt())
        assertEquals(all.map { it.id }.toSet(), api.readReleaseData(8).releases.nodeList.map { it.id }.toSet().let { it + all.first().id })
        assertTrue(api.readReleaseData(8).releases.nodeList.map { it.id }.containsAll(all.drop(1).map { it.id }))
        APIDataStoreImpl.loadUpdaterSnapshot(api)
    }

    @Test
    fun `legacy generation records remain readable without inventing an identity`(): Unit = runBlocking {
        val client = MongoClient()
        val api = MongoApiPersistence(client)
        val time = TimeSource.now()
        api.updateUpdatedTime(time, "legacy", 7)
        client.getDatabase().getCollection<Document>(MongoApiPersistence.UPDATE_TIME_DB)
            .updateMany(Document(), Document("\$unset", Document("generationId", "")))

        val restarted = MongoApiPersistence(MongoClient())
        val expected = UpdatedInfo(time, "legacy", 7)
        assertEquals(expected, restarted.getUpdatedAt())
        assertEquals(expected, restarted.getUpdatedAt())
    }

    @Test
    fun `raw updater snapshot loads all persisted feature versions and internal download counts`(): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val template = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val version = (System.getenv("MAX_VERSION_TO_LOAD") ?: "100").toInt() + 1
        val internal = Release("above-serving-cap", template.release_type, template.release_link, template.release_name,
            template.timestamp, template.updated_at, emptyArray(), 123, template.vendor,
            VersionData(version, 0, 0, null, null, 1, null, ""))
        api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(version, Releases(listOf(internal))))), "all-versions")

        val restarted = MongoApiPersistence(MongoClient())
        val (snapshot, generation) = APIDataStoreImpl.loadUpdaterSnapshotWithInfo(restarted)
        val stored = snapshot.getFeatureRelease(version)!!.releases.nodes.getValue(internal.id)
        assertEquals(internal, stored)
        assertEquals(123L, stored.download_count)
        assertEquals("all-versions", generation.checksum)
    }

    @Test
    fun `rejected release writes leave the published catalogue intact and permit recovery`(): Unit = runBlocking {
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
        // Reject all release writes; nothing may be removed before its replacement exists.
        client.getDatabase().runCommand(Document("collMod", MongoApiPersistence.RELEASE_DB)
            .append("validator", Document("generationFailureTest", Document("\$exists", true))))
        try {
            repeat(2) {
                val error = assertThrows(MongoBulkWriteException::class.java) {
                    runBlocking { api.updateAllRepos(target, targetChecksum) }
                }
                assertTrue(error.writeErrors.any { it.code == 121 }, "Propagate Mongo's document validation failure")
                val restarted = MongoApiPersistence(MongoClient())
                assertEquals(setOf(oldRelease.id), restarted.readReleaseData(8).releases.nodes.keys)
                assertEquals(setOf(omitted.id), restarted.readReleaseData(11).releases.nodes.keys)
                assertEquals(oldChecksum, restarted.getUpdatedAt().checksum)
                APIDataStoreImpl.loadUpdaterSnapshot(restarted)
            }
        } finally {
            client.getDatabase().runCommand(Document("collMod", MongoApiPersistence.RELEASE_DB)
                .append("validator", Document()))
        }

        api.updateAllRepos(target, targetChecksum)
        val recovered = MongoApiPersistence(MongoClient())
        assertEquals(setOf(replacement.id), recovered.readReleaseData(8).releases.nodes.keys)
        assertEquals(setOf(omitted.id), recovered.readReleaseData(11).releases.nodes.keys)
        assertEquals(targetChecksum, recovered.getUpdatedAt().checksum)
    }

    @Test
    fun `generation write failure propagates original exception without publishing target`(): Unit = runBlocking {
        val release = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first()
        val replacement = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.drop(1).first()
        val before = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(release)))))
        val target = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(replacement)))))
        val oldChecksum = V3Updater.calculateChecksum(before)
        val targetChecksum = V3Updater.calculateChecksum(target)
        val failure = IllegalStateException("Injected generation write failure")
        var fail = false
        val api = object : MongoApiPersistence(MongoClient()) {
            override suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
                if (fail && checksum == targetChecksum) throw failure
                super.updateUpdatedTime(dateTime, checksum, hashCode)
            }
        }
        api.updateAllRepos(before, oldChecksum)
        fail = true
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            runBlocking { api.updateAllRepos(target, targetChecksum) }
        })
        val restarted = MongoApiPersistence(MongoClient())
        assertEquals(oldChecksum, restarted.getUpdatedAt().checksum)
        assertTrue(restarted.readReleaseData(8).releases.nodes.keys.containsAll(setOf(replacement.id)))
        APIDataStoreImpl.loadUpdaterSnapshot(restarted)
        fail = false
        api.updateAllRepos(target, targetChecksum)
        assertEquals(targetChecksum, restarted.getUpdatedAt().checksum)
        assertEquals(setOf(replacement.id), restarted.readReleaseData(8).releases.nodes.keys)
    }

    @Test
    fun `empty snapshot retains persisted features across a restart`(): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val before = AdoptRepos(listOf(8, 11).map { version ->
            FeatureRelease(version, Releases(listOf(BaseTest.adoptRepos.getFeatureRelease(version)!!.releases.nodeList.first())))
        })
        api.updateAllRepos(before, V3Updater.calculateChecksum(before))
        val target = AdoptRepos(emptyList())
        val checksum = V3Updater.calculateChecksum(target)
        api.updateAllRepos(target, checksum)

        val restarted = MongoApiPersistence(MongoClient())
        for ((version, releases) in before.repos) {
            val stored = restarted.readReleaseData(version)
            assertEquals(releases.releases.nodes, stored.releases.nodes)
            for (release in releases.releases.nodeList) {
                assertEquals(release.download_count, stored.releases.nodes[release.id]!!.download_count)
            }
        }
        assertEquals(checksum, restarted.getUpdatedAt().checksum)
        assertEquals(target.hashCode(), restarted.getUpdatedAt().hashCode)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `empty and omitted features retain internal releases while other features update`(omit: Boolean): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val retained = Release(BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first(), emptyArray())
        val oldRelease = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val replacement = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.drop(1).first()
        val before = AdoptRepos(listOf(
            FeatureRelease(8, Releases(listOf(retained))), FeatureRelease(11, Releases(listOf(oldRelease)))
        ))
        api.updateAllRepos(before, "before-empty-feature")
        assertEquals(setOf(retained.id), api.readReleaseData(8).releases.nodes.keys)
        val after = AdoptRepos(listOf(FeatureRelease(11, Releases(listOf(replacement)))) +
            if (omit) emptyList() else listOf(FeatureRelease(8, Releases(emptyList()))))
        api.updateAllRepos(after, "after-empty-feature")

        val restarted = MongoApiPersistence(MongoClient())
        val stored = restarted.readReleaseData(8).releases.nodes
        assertEquals(setOf(retained.id), stored.keys)
        val preserved = stored.getValue(retained.id)
        assertEquals(retained, preserved)
        assertEquals(retained.download_count, preserved.download_count)
        assertEquals(setOf(replacement.id), restarted.readReleaseData(11).releases.nodes.keys)
        assertEquals("after-empty-feature", restarted.getUpdatedAt().checksum)
    }

    @Test
    fun `nonempty feature replacement removes obsolete releases without deleting omitted features`(): Unit = runBlocking {
        val api = MongoApiPersistence(MongoClient())
        val releases = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.take(2)
        val retained = BaseTest.adoptRepos.getFeatureRelease(11)!!.releases.nodeList.first()
        val before = AdoptRepos(listOf(
            FeatureRelease(8, Releases(releases)), FeatureRelease(11, Releases(listOf(retained)))
        ))
        api.updateAllRepos(before, "before-replacement")
        val replacement = releases.last()
        api.updateAllRepos(AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(replacement))))), "after-replacement")

        val restarted = MongoApiPersistence(MongoClient())
        assertEquals(setOf(replacement.id), restarted.readReleaseData(8).releases.nodes.keys)
        assertEquals(setOf(retained.id), restarted.readReleaseData(11).releases.nodes.keys)
        assertEquals("after-replacement", restarted.getUpdatedAt().checksum)
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
    fun `release notes are persisted and upserted atomically`() {
        runBlocking {
            val apiPersistence = MongoApiPersistence(MongoClient())

            val releaseNotes1 = ReleaseNotes(
                VersionData(17, 0, 1, "open", 1, 1, null, "17.0.1+1"),
                Vendor.adoptium,
                "release-id-123",
                "jdk-17.0.1+1",
                listOf(ReleaseNote("JDK-1234", "Title 1", "P1", "core-libs", "java.lang", "https://bugs.openjdk.org/browse/JDK-1234", "Enhancement", null, null))
            )

            apiPersistence.putReleaseNote(releaseNotes1)
            assertEquals(true, apiPersistence.hasReleaseNotesForGithubId(GitHubId("release-id-123")))
            assertEquals(releaseNotes1, apiPersistence.getReleaseNotes(Vendor.adoptium, "jdk-17.0.1+1"))

            // Upsert / replace existing release notes
            val releaseNotes2 = ReleaseNotes(
                VersionData(17, 0, 1, "open", 1, 1, null, "17.0.1+1"),
                Vendor.adoptium,
                "release-id-123",
                "jdk-17.0.1+1",
                listOf(ReleaseNote("JDK-5678", "Title 2", "P2", "core-libs", "java.util", "https://bugs.openjdk.org/browse/JDK-5678", "Bug", null, null))
            )

            apiPersistence.putReleaseNote(releaseNotes2)
            assertEquals(releaseNotes2, apiPersistence.getReleaseNotes(Vendor.adoptium, "jdk-17.0.1+1"))
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
