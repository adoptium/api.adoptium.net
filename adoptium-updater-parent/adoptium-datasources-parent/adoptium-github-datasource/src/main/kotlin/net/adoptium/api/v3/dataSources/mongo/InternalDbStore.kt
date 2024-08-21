package net.adoptium.api.v3.dataSources.mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoInterface
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.Document
import java.time.ZonedDateTime

interface InternalDbStore {
    fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job
    suspend fun getCachedWebpage(url: String): CacheDbEntry?
    suspend fun updateCheckedTime(url: String, dateTime: ZonedDateTime)
}

@ApplicationScoped
open class InternalDbStoreImpl @Inject constructor(mongoClient: MongoClient) : MongoInterface(), InternalDbStore {
    private val webCache: MongoCollection<CacheDbEntry> = createCollection(mongoClient.getDatabase(), "web-cache")

    init {
        runBlocking {
            webCache.createIndex(Document.parse("""{"url":1}"""), IndexOptions().background(true))
        }
    }

    override fun putCachedWebpage(url: String, lastModified: String?, date: ZonedDateTime, data: String?): Job {
        return GlobalScope.launch {
            webCache.replaceOne(
                Document("url", url),
                CacheDbEntry(url, lastModified, date, data),
                ReplaceOptions().upsert(true)
            )
        }
    }

    override suspend fun getCachedWebpage(url: String): CacheDbEntry? {
        return webCache.find(Document("url", url)).firstOrNull()
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
