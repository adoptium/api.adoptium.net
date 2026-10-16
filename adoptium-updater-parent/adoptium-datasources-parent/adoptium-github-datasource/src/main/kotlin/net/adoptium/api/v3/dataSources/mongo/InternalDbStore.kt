package net.adoptium.api.v3.dataSources.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.UpdateOptions
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

    // Demand is the default, including direct uncached fetches. Background validation passes null.
    fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime? = TimeSource.now()): Job
    suspend fun getCachedWebpage(url: String): CacheDbEntry?
    suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime)
    suspend fun evictUnusedCache(cutoff: ZonedDateTime = TimeSource.now().minusDays(CACHE_RETENTION_DAYS)): Long
}

@ApplicationScoped
open class InternalDbStoreImpl @Inject constructor(mongoClient: MongoClient) : MongoInterface(), InternalDbStore {
    private val webCache: MongoCollection<CacheDbEntry> = createCollection(mongoClient.getDatabase(), "web-cache")

    init {
        runBlocking {
            webCache.createIndex(Document.parse("""{"url":1}"""), IndexOptions().background(true))
            // Legacy entries get one grace period, independently of their validation age.
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

    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Job {
        val insertedAt = accessedAt ?: TimeSource.now()
        return GlobalScope.launch {
            val update = Document("\$set", Document("lastModified", lastModified)
                .append("lastChecked", Date.from(date.toInstant()))
                .append("data", data))
            if (accessedAt == null) {
                update.append("\$setOnInsert", Document("lastAccessed", Date.from(insertedAt.toInstant())))
            } else {
                update.append("\$max", Document("lastAccessed", Date.from(accessedAt.toInstant())))
            }
            webCache.updateOne(
                Document("url", url),
                update,
                UpdateOptions().upsert(true)
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

    override suspend fun evictUnusedCache(cutoff: ZonedDateTime): Long {
        return webCache.deleteMany(
            Document("lastAccessed", Document("\$lte", Date.from(cutoff.toInstant())))
        ).deletedCount
    }
}
