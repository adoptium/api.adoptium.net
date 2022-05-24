package net.adoptium.marketplace.dataSources.persitence.mongo

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.runBlocking
import net.adoptium.marketplace.dataSources.ReleaseInfo
import net.adoptium.marketplace.dataSources.TimeSource
import net.adoptium.marketplace.dataSources.persitence.VendorPersistence
import net.adoptium.marketplace.schema.OpenjdkVersionData
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.ReleaseUpdateInfo
import net.adoptium.marketplace.schema.Vendor
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonElement
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.util.KMongoUtil
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

open class MongoVendorPersistence constructor(
    mongoClient: MongoClient,
    private val vendor: Vendor
) : MongoInterface(mongoClient), VendorPersistence {

    private val releasesCollection: CoroutineCollection<Release> = initDb(database, vendor.name + "_" + RELEASE_DB)
    private val releaseInfoCollection: CoroutineCollection<ReleaseInfo> = initDb(database, vendor.name + "_" + RELEASE_INFO_DB)
    private val updateTimeCollection: CoroutineCollection<UpdatedInfo> = initDb(database, vendor.name + "_" + UPDATE_TIME_DB, MongoVendorPersistence::initUptimeDb)
    private val updateLogCollection: CoroutineCollection<ReleaseUpdateInfo> = initDb(database, vendor.name + "_" + UPDATE_LOG)


    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        const val RELEASE_DB = "release"
        const val RELEASE_INFO_DB = "releaseInfo"
        const val UPDATE_TIME_DB = "updateTime"
        const val UPDATE_LOG = "updateLog"

        fun initUptimeDb(collection: CoroutineCollection<UpdatedInfo>) {
            runBlocking {
                try {
                    val set = KMongoUtil.filterIdToBson(UpdatedInfo(Date.from(TimeSource.now().minusMinutes(5).toInstant())))
                    collection.insertOne(UpdatedInfo(Date.from(TimeSource.now().minusMinutes(5).toInstant())))
                    set.toString()
                } catch (e: Exception) {
                    LOGGER.error("Failed to run init", e)
                }
            }
        }
    }

    override suspend fun writeReleases(releases: ReleaseList): ReleaseUpdateInfo {

        val added = mutableListOf<Release>()
        val updated = mutableListOf<Release>()

        releases
            .releases
            .filter { it.vendor == vendor }
            .forEach { release ->

                val matcher = releaseMatcher(release)

                val docCount = releasesCollection
                    .countDocuments(matcher, CountOptions())

                if (docCount > 1) {
                    LOGGER.warn("MULTIPLE DOCUMENTS MATCH $vendor ${release.releaseName} ${release.releaseLink} ${release.openjdkVersionData}. This might cause issues.")
                }

                val result = releasesCollection
                    .updateOne(matcher, release, UpdateOptions().upsert(true))

                if (result.upsertedId != null) {
                    added.add(release)
                } else {
                    if (result.modifiedCount > 0) {
                        updated.add(release)
                    }
                }
            }

        val currentDb = this.getAllReleases()

        val removed = currentDb
            .releases
            .filter { currentRelease ->
                releases.releases.none {
                    it.vendor == currentRelease.vendor &&
                        it.releaseName == currentRelease.releaseName &&
                        it.releaseLink == currentRelease.releaseLink &&
                        it.openjdkVersionData.compareTo(currentRelease.openjdkVersionData) == 0
                }
            }
            .map { toRemove ->
                LOGGER.info("Removing old release ${toRemove.releaseName}")
                val matcher = releaseMatcher(toRemove)
                val deleted = releasesCollection.deleteMany(matcher)
                if (deleted.deletedCount != 1L) {
                    LOGGER.error("Failed to delete release ${toRemove.releaseName}")
                }
                return@map toRemove
            }

        if (added.isNotEmpty() || updated.isNotEmpty() || removed.isNotEmpty()) {
            updateUpdatedTime(Date())
        }

        val result = ReleaseUpdateInfo(ReleaseList(added), ReleaseList(updated), ReleaseList(removed), Date())
        logUpdate(result)
        return result
    }

    private suspend fun logUpdate(result: ReleaseUpdateInfo) {
        updateLogCollection.insertOne(result)
        updateTimeCollection.deleteMany(Document("timestamp", BsonDocument("\$lt", BsonDateTime(result.timestamp.toInstant().minus(Duration.ofDays(30)).toEpochMilli()))))
    }

    override suspend fun setReleaseInfo(releaseInfo: ReleaseInfo) {
        releaseInfoCollection.deleteMany(releaseVersionDbEntryMatcher())
        releaseInfoCollection.updateOne(
            releaseVersionDbEntryMatcher(),
            releaseInfo,
            UpdateOptions().upsert(true)
        )
    }

    private suspend fun updateUpdatedTime(dateTime: Date) {
        updateTimeCollection.updateOne(
            Document(),
            UpdatedInfo(dateTime),
            UpdateOptions().upsert(true)
        )
        updateTimeCollection.deleteMany(Document("time", BsonDocument("\$lt", BsonDateTime(dateTime.toInstant().toEpochMilli()))))
    }

    override suspend fun getUpdatedInfoIfUpdatedSince(since: Date): UpdatedInfo? {
        val result = updateTimeCollection.find(Document("time", BsonDocument("\$gt", BsonDateTime(since.toInstant().toEpochMilli()))))

        return result.first()
    }

    override suspend fun getReleaseVendorStatus(): List<ReleaseUpdateInfo> {
        return updateLogCollection.find(EMPTY_BSON).toList()
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return releaseInfoCollection.findOne(releaseVersionDbEntryMatcher())
    }

    override suspend fun getAllReleases(): ReleaseList {
        return ReleaseList(releasesCollection.find(EMPTY_BSON).toList())
    }

    private fun releaseVersionDbEntryMatcher() = Document("tip_version", BsonDocument("\$exists", BsonBoolean(true)))

    private fun releaseMatcher(release: Release): BsonDocument {

        var matcher = listOf(
            BsonElement("release_name", BsonString(release.releaseName)),
            BsonElement("vendor", BsonString(release.vendor.name))
        )

        if (release.releaseLink != null) {
            matcher = matcher.plus(BsonElement("release_link", BsonString(release.releaseLink)));
        }

        return BsonDocument(matcher
            .plus(versionMatcher(release.openjdkVersionData))
        )
    }

    private fun versionMatcher(openjdkVersionData: OpenjdkVersionData): List<BsonElement> {
        var matcher = listOf(
            BsonElement("version_data.openjdk_version", BsonString(openjdkVersionData.openjdk_version)),
            BsonElement("version_data.major", BsonInt32(openjdkVersionData.major))
        )

        if (openjdkVersionData.build.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.build", BsonInt32(openjdkVersionData.build.get())))
        }
        if (openjdkVersionData.minor.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.minor", BsonInt32(openjdkVersionData.minor.get())))
        }
        if (openjdkVersionData.pre.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.pre", BsonString(openjdkVersionData.pre.get())))
        }
        if (openjdkVersionData.optional.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.optional", BsonString(openjdkVersionData.optional.get())))
        }
        if (openjdkVersionData.patch.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.patch", BsonInt32(openjdkVersionData.patch.get())))
        }
        if (openjdkVersionData.security.isPresent) {
            matcher = matcher.plus(BsonElement("version_data.security", BsonInt32(openjdkVersionData.security.get())))
        }

        return matcher
    }
}
