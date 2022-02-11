package net.adoptium.marketplace.dataSources.persitence.mongo

import com.mongodb.client.model.UpdateOptions
import net.adoptium.marketplace.dataSources.ReleaseInfo
import net.adoptium.marketplace.dataSources.TimeSource
import net.adoptium.marketplace.dataSources.persitence.VendorPersistence
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.schema.VersionData
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonElement
import org.bson.BsonInt32
import org.bson.BsonString
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

open class MongoVendorPersistence constructor(
    mongoClient: MongoClient,
    private val vendor: Vendor
) : MongoInterface(mongoClient), VendorPersistence {
    private val releasesCollection: CoroutineCollection<Release> = createCollection(database, vendor.name + "_" + RELEASE_DB)
    private val releaseInfoCollection: CoroutineCollection<ReleaseInfo> = createCollection(database, vendor.name + "_" + RELEASE_INFO_DB)
    private val updateTimeCollection: CoroutineCollection<UpdatedInfo> = createCollection(database, vendor.name + "_" + UPDATE_TIME_DB)

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        const val RELEASE_DB = "release"
        const val RELEASE_INFO_DB = "releaseInfo"
        const val UPDATE_TIME_DB = "updateTime"
    }

    override suspend fun writeReleases(releases: ReleaseList): List<Release> {
        val updated = releases
            .releases
            .filter { it.vendor == vendor }
            .map { release ->
                val result = releasesCollection
                    .updateOne(releaseMatcher(release), release, UpdateOptions().upsert(false))
                return@map if (result.modifiedCount == 0L) {
                    release
                } else {
                    null
                }
            }
            .filterNotNull()

        if (updated.isNotEmpty()) {
            val newTime = updated
                .map { it.timestamp }
                .maxOf { it }

            updateUpdatedTime(newTime.toInstant().atZone(TimeSource.ZONE))
        }

        return updated
    }

    override suspend fun setReleaseInfo(releaseInfo: ReleaseInfo) {
        releaseInfoCollection.deleteMany(releaseVersionDbEntryMatcher())
        releaseInfoCollection.updateOne(
            releaseVersionDbEntryMatcher(),
            releaseInfo,
            UpdateOptions().upsert(true)
        )
    }

    private suspend fun updateUpdatedTime(dateTime: ZonedDateTime) {
        updateTimeCollection.updateOne(
            Document(),
            UpdatedInfo(dateTime),
            UpdateOptions().upsert(true)
        )
        updateTimeCollection.deleteMany(Document("time", BsonDocument("\$lt", BsonDateTime(dateTime.toInstant().toEpochMilli()))))
    }

    override suspend fun getUpdatedAt(): UpdatedInfo {
        val info = updateTimeCollection.findOne()
        // if we have no existing time, make it 5 mins ago, should only happen on first time the db is used
        return info ?: UpdatedInfo(TimeSource.now().minusMinutes(5))
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return releaseInfoCollection.findOne(releaseVersionDbEntryMatcher())
    }

    override suspend fun getAllReleases(): ReleaseList {
        return ReleaseList(releasesCollection.find(Document()).toList())
    }

    private fun releaseVersionDbEntryMatcher() = Document("tip_version", BsonDocument("\$exists", BsonBoolean(true)))

    private fun releaseMatcher(release: Release): BsonDocument {
        return BsonDocument(
            listOf(
                BsonElement("release_name", BsonString(release.release_name)),
                BsonElement("release_link", BsonString(release.release_link)),
                BsonElement("vendor", BsonString(release.vendor.name)),
                BsonElement("date", BsonDateTime(release.timestamp.toInstant().toEpochMilli())),
                BsonElement("version_data", versionMatcher(release.version_data)),
            )
        )
    }

    private fun versionMatcher(versionData: VersionData): BsonDocument {
        return BsonDocument(
            listOf(
                BsonElement("openjdk_version", BsonString(versionData.openjdk_version)),
                BsonElement("pre", BsonString(versionData.pre)),
                BsonElement("optional", BsonString(versionData.optional)),
                BsonElement("build", BsonInt32(versionData.build)),
                BsonElement("major", BsonInt32(versionData.major)),
                BsonElement("minor", BsonInt32(versionData.minor)),
                BsonElement("patch", BsonInt32(versionData.patch)),
                BsonElement("security", BsonInt32(versionData.security)),
            )
        )
    }
}
