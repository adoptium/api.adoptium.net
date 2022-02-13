package net.adoptium.marketplace.dataSources.persitence.mongo

import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.runBlocking
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
import org.litote.kmongo.EMPTY_BSON
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.util.KMongoUtil
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

open class MongoVendorPersistence constructor(
    mongoClient: MongoClient,
    private val vendor: Vendor
) : MongoInterface(mongoClient), VendorPersistence {

    private val releasesCollection: CoroutineCollection<Release> = initUpdateTimeDb(database, vendor.name + "_" + RELEASE_DB)
    private val releaseInfoCollection: CoroutineCollection<ReleaseInfo> = initUpdateTimeDb(database, vendor.name + "_" + RELEASE_INFO_DB)
    private val updateTimeCollection: CoroutineCollection<UpdatedInfo> = initUpdateTimeDb(database, vendor.name + "_" + UPDATE_TIME_DB, MongoVendorPersistence::initUptimeDb)

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        const val RELEASE_DB = "release"
        const val RELEASE_INFO_DB = "releaseInfo"
        const val UPDATE_TIME_DB = "updateTime"

        fun initUptimeDb(collection: CoroutineCollection<UpdatedInfo>) {
            runBlocking {
                collection.insertOne(UpdatedInfo(TimeSource.now().minusMinutes(5)))
            }
        }
    }

    override suspend fun writeReleases(releases: ReleaseList): ReleaseList {
        val updated = releases
            .releases
            .filter { it.vendor == vendor }
            .map { release ->

                val set = KMongoUtil.filterIdToBson(release)

                val updateQuery = """
                    {
                        '${'$'}setOnInsert': ${set.toBsonDocument()}
                    }
                    """.trimIndent()

                val matcher = releaseMatcher(release)
                val result = releasesCollection
                    .updateOne(matcher, BsonDocument.parse(updateQuery), UpdateOptions().upsert(true))

                val count = releasesCollection.countDocuments(EMPTY_BSON)
                println(count)
                return@map if (result.upsertedId != null) {
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

        return ReleaseList(updated)
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

    override suspend fun getUpdatedInfoIfUpdatedSince(since: ZonedDateTime): UpdatedInfo? {
        val result = updateTimeCollection.find(Document("time", BsonDocument("\$gt", BsonDateTime(since.toInstant().toEpochMilli()))))
        return result.first()
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return releaseInfoCollection.findOne(releaseVersionDbEntryMatcher())
    }

    override suspend fun getAllReleases(): ReleaseList {
        return ReleaseList(releasesCollection.find(EMPTY_BSON).toList())
    }

    private fun releaseVersionDbEntryMatcher() = Document("tip_version", BsonDocument("\$exists", BsonBoolean(true)))

    private fun releaseMatcher(release: Release): BsonDocument {
        return BsonDocument(
            listOf(
                BsonElement("release_name", BsonString(release.release_name)),
                BsonElement("release_link", BsonString(release.release_link)),
                BsonElement("vendor", BsonString(release.vendor.name)),
                BsonElement("timestamp", BsonDateTime(release.timestamp.toInstant().toEpochMilli())),
            )
                .plus(versionMatcher(release.version_data))
        )
    }

    private fun versionMatcher(versionData: VersionData): List<BsonElement> {
        return listOf(
            BsonElement("version_data.openjdk_version", BsonString(versionData.openjdk_version)),
            BsonElement("version_data.pre", BsonString(versionData.pre)),
            BsonElement("version_data.optional", BsonString(versionData.optional)),
            BsonElement("version_data.build", BsonInt32(versionData.build)),
            BsonElement("version_data.major", BsonInt32(versionData.major)),
            BsonElement("version_data.minor", BsonInt32(versionData.minor)),
            BsonElement("version_data.patch", BsonInt32(versionData.patch)),
            BsonElement("version_data.security", BsonInt32(versionData.security)),
        )
    }
}
