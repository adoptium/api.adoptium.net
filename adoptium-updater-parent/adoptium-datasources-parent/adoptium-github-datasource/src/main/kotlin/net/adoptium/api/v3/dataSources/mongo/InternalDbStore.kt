package net.adoptium.api.v3.dataSources.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoCollection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoInterface
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.Document
import java.time.ZonedDateTime
import java.util.Date
import java.util.concurrent.TimeUnit

interface InternalDbStore {
    companion object {
        const val CACHE_RETENTION_DAYS = 30L
    }

    fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job
    suspend fun getCachedWebpage(url: String): CacheDbEntry?
    suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime)
    suspend fun evictUnusedCache(now: ZonedDateTime = TimeSource.now()): Long
}

@ApplicationScoped
open class InternalDbStoreImpl @Inject constructor(mongoClient: MongoClient) : MongoInterface(), InternalDbStore {
    private val webCache: MongoCollection<CacheDbEntry> = createCollection(mongoClient.getDatabase(), "web-cache")

    init {
        runBlocking {
            webCache.createIndex(Document.parse("""{"url":1}"""), IndexOptions().background(true))
            // Old entries have no usage history. Give them one retention period to
            // be reused rather than treating a possibly ancient validation as last use.
            webCache.updateMany(
                Document("lastAccessed", null),
                Document("\$set", Document("lastAccessed", Date.from(TimeSource.now().toInstant())))
            )
            webCache.createIndex(
                Document("lastAccessed", 1),
                IndexOptions().expireAfter(InternalDbStore.CACHE_RETENTION_DAYS, TimeUnit.DAYS)
            )
        }
    }

    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job {
        return GlobalScope.launch {
            webCache.replaceOne(
                Document("url", url),
                CacheDbEntry(url, lastModified, date, data, date),
                ReplaceOptions().upsert(true)
            )
        }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return webCache.findOneAndUpdate(
            Document("url", url),
            Document("\$max", Document("lastAccessed", Date.from(TimeSource.now().toInstant()))),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
    }

    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
        webCache.updateOne(
            Document("url", url),
            BsonDocument(
                "\$set",
                BsonDocument("lastChecked", BsonDateTime(dateTime.toInstant().toEpochMilli()))
            )
        )
    }

    override suspend fun evictUnusedCache(now: ZonedDateTime): Long {
        val cutoff = now.minusDays(InternalDbStore.CACHE_RETENTION_DAYS)
        return webCache.deleteMany(
            Document("lastAccessed", Document("\$lte", Date.from(cutoff.toInstant())))
        ).deletedCount
    }
}
