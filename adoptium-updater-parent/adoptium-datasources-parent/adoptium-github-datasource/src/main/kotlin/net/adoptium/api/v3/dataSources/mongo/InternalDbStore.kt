package net.adoptium.api.v3.dataSources.mongo

import com.mongodb.MongoCommandException
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.kotlin.client.coroutine.MongoCollection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoInterface
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.Document
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.Date
import java.util.concurrent.TimeUnit

interface InternalDbStore {
    companion object {
        const val CACHE_RETENTION_DAYS = 30L
    }

    // Background writes preserve usage; demand writes advance it monotonically.
    fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime? = null): Deferred<Unit>
    suspend fun getCachedWebpage(url: String): CacheDbEntry?
    suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime)
}

@ApplicationScoped
open class InternalDbStoreImpl @Inject constructor(mongoClient: MongoClient) : MongoInterface(), InternalDbStore {
    private companion object {
        const val INDEX_NOT_FOUND = 27
    }

    private val webCache: MongoCollection<CacheDbEntry> = createCollection(mongoClient.getDatabase(), "web-cache")

    init {
        runBlocking {
            val urlIndex = retryMongoOperation("read cache indexes") { webCache.listIndexes<Document>().toList() }
                .singleOrNull { it["key"] == Document("url", 1) }
            if (urlIndex?.getBoolean("unique", false) != true) {
                // Duplicate content has no trustworthy ordering. Invalidate those keys and refetch.
                val duplicates = retryMongoOperation("read duplicate cache keys") {
                    webCache.aggregate<Document>(listOf(
                        Document($$"$group", Document("_id", $$"$url").append("count", Document($$"$sum", 1))),
                        Document($$"$match", Document("count", Document($$"$gt", 1)))
                    )).toList()
                }
                for (duplicate in duplicates) {
                    val removed = retryMongoOperation("delete duplicate cache entries") {
                        webCache.deleteMany(Document("url", duplicate["_id"])).deletedCount
                    }
                    LoggerFactory.getLogger(InternalDbStoreImpl::class.java)
                        .warn("Invalidated {} duplicate cache entries for {}", removed, duplicate["_id"])
                }
                if (urlIndex != null) retryMongoOperation("drop nonunique cache index") {
                    try {
                        webCache.dropIndex(urlIndex.getString("name"))
                    } catch (e: MongoCommandException) {
                        // A retry after a lost acknowledgement finds the index already dropped
                        if (e.errorCode != INDEX_NOT_FOUND) throw e
                    }
                }
            }
            retryMongoOperation("create unique cache index") {
                webCache.createIndex(Document("url", 1), IndexOptions().unique(true).background(true))
            }
            // Old entries have no usage history. Give them one retention period to
            // be reused rather than treating a possibly ancient validation as last use.
            val initialAccessTime = Date.from(TimeSource.now().toInstant())
            retryMongoOperation("initialize cache usage") {
                webCache.updateMany(Document("lastAccessed", null), Document($$"$set", Document("lastAccessed", initialAccessTime)))
            }
            retryMongoOperation("create cache retention index") {
                webCache.createIndex(
                    Document("lastAccessed", 1),
                    IndexOptions().expireAfter(InternalDbStore.CACHE_RETENTION_DAYS, TimeUnit.DAYS)
                )
            }
        }
    }

    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?, accessedAt: ZonedDateTime?): Deferred<Unit> {
        return GlobalScope.async {
            val update = Document($$"$set", Document("lastModified", lastModified)
                .append("lastChecked", Date.from(date.toInstant()))
                .append("data", data))
            if (accessedAt == null) {
                update.append($$"$setOnInsert", Document("lastAccessed", Date.from(date.toInstant())))
            } else {
                update.append($$"$max", Document("lastAccessed", Date.from(accessedAt.toInstant())))
            }
            retryMongoOperation("write cached webpage") {
                webCache.updateOne(Document("url", url), update, UpdateOptions().upsert(true))
            }
            Unit
        }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        val accessedAt = Date.from(TimeSource.now().toInstant())
        return retryMongoOperation("read and touch cached webpage") {
            webCache.findOneAndUpdate(
                Document("url", url),
                Document($$"$max", Document("lastAccessed", accessedAt)),
                FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            )
        }
    }

    override suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime) {
        retryMongoOperation("update cache validation time") {
            webCache.updateOne(
                Document("url", url),
                BsonDocument(
                    $$"$set",
                    BsonDocument("lastChecked", BsonDateTime(dateTime.toInstant().toEpochMilli()))
                )
            )
        }
    }
}
