package net.adoptopenjdk.api.v3.dataSources.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.MongoInterface
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import java.time.ZonedDateTime
import javax.enterprise.inject.Default
import javax.inject.Inject
import javax.inject.Singleton

interface InternalDbStore {
    fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job
    suspend fun getCachedWebpage(url: String): CacheDbEntry?
    suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime)
}

@Singleton
@Default
class InternalDbStoreImpl @Inject constructor(mongoClient: MongoClient) : MongoInterface(mongoClient), InternalDbStore {
    private val webCache: CoroutineCollection<CacheDbEntry> = createCollection(database, "web-cache")

    init {
        runBlocking {
            webCache.createIndex("""{"url":1}""", IndexOptions().background(true))
        }
    }

    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job {
        return GlobalScope.launch {
            webCache.updateOne(
                Document("url", url),
                CacheDbEntry(url, lastModified, date, data),
                UpdateOptions().upsert(true),
                false
            )
        }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return webCache.findOne(Document("url", url))
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
}
