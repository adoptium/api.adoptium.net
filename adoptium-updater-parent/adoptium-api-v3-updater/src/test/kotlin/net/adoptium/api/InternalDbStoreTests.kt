package net.adoptium.api

import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStoreImpl
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class InternalDbStoreTests : MongoTest() {
    private lateinit var mongoClient: MongoClient
    private lateinit var collection: MongoCollection<Document>

    @BeforeEach
    fun clearCache() {
        runBlocking {
            mongoClient = MongoClient()
            collection = mongoClient.getDatabase().getCollection<Document>("web-cache")
            collection.deleteMany(Document())
        }
    }

    @Test
    fun `checked time is set`() {
        runBlocking {
            val internalDbStore = InternalDbStoreImpl(mongoClient)

            val now = TimeSource.now()
            val data = CacheDbEntry("foo", "bar", now, "some data")
            internalDbStore
                .putCachedWebpage(data.url, data.lastModified, data.lastChecked!!, data.data)
                .join()

            val cachedData = internalDbStore.getCachedWebpage("foo")
            assertNotNull(cachedData!!.lastAccessed)
            assertEquals(data, cachedData.copy(lastAccessed = null))

            val newTime = TimeSource.now().plusMinutes(1)

            internalDbStore.updateCheckedTime("foo", newTime)
            val updated = internalDbStore.getCachedWebpage("foo")

            assertEquals(CacheDbEntry(data.url, data.lastModified, newTime, data.data), updated!!.copy(lastAccessed = null))
        }
    }

    @Test
    fun `unused entries expire at the thirty day boundary`() = runBlocking {
        val store = InternalDbStoreImpl(mongoClient)
        val now = TimeSource.now()
        store.putCachedWebpage("boundary", null, now, "data").join()

        assertEquals(0L, store.evictUnusedCache(now.plusDays(30).minusSeconds(1)))
        assertEquals(1L, store.evictUnusedCache(now.plusDays(30)))
        assertNull(store.getCachedWebpage("boundary"))
    }

    @Test
    fun `cache reads retain actively used entries without changing validation time`() = runBlocking {
        val store = InternalDbStoreImpl(mongoClient)
        val now = TimeSource.now()
        val old = now.minusDays(20)
        store.putCachedWebpage("active", null, old, "active data").join()
        store.putCachedWebpage("unused", null, old, "unused data").join()

        val active = store.getCachedWebpage("active")!!

        assertEquals(old, active.lastChecked)
        assertTrue(!active.lastAccessed!!.isBefore(now))
        assertEquals(1L, store.evictUnusedCache(now.plusDays(20)))
        assertEquals("active data", store.getCachedWebpage("active")!!.data)
        assertNull(store.getCachedWebpage("unused"))
    }

    @Test
    fun `HTTP revalidation does not keep unused entries alive`() = runBlocking {
        val store = InternalDbStoreImpl(mongoClient)
        val now = TimeSource.now()
        store.putCachedWebpage("checked-only", null, now.minusDays(20), "data").join()

        store.updateCheckedTime("checked-only", now)

        assertEquals(1L, store.evictUnusedCache(now.plusDays(20)))
        assertNull(store.getCachedWebpage("checked-only"))
    }

    @Test
    fun `last access uses a BSON date and a thirty day TTL index`() = runBlocking {
        val store = InternalDbStoreImpl(mongoClient)
        val now = TimeSource.now()
        store.putCachedWebpage("ttl", null, now, "data").join()

        val document = collection.find(Document("url", "ttl")).firstOrNull()!!
        val index = collection.listIndexes<Document>().toList().single {
            it.get("key", Document::class.java) == Document("lastAccessed", 1)
        }

        assertEquals(Date.from(now.toInstant()), document.getDate("lastAccessed"))
        assertEquals(TimeUnit.DAYS.toSeconds(30), (index["expireAfterSeconds"] as Number).toLong())
    }

    @Test
    fun `legacy cache entries get a retention grace period`() = runBlocking {
        val now = TimeSource.now()
        collection.insertOne(Document("url", "legacy")
            .append("lastChecked", Date.from(now.minusDays(60).toInstant()))
            .append("data", "legacy data"))

        val store = InternalDbStoreImpl(mongoClient)
        val document = collection.find(Document("url", "legacy")).firstOrNull()!!

        assertTrue(!document.getDate("lastAccessed").toInstant().isBefore(now.toInstant()))
        assertEquals(0L, store.evictUnusedCache(now.plusDays(29)))
        assertEquals(1L, store.evictUnusedCache(now.plusDays(31)))
    }

    @Test
    fun `initialization does not reset existing last access timestamps`() = runBlocking {
        val now = TimeSource.now()
        val accessed = Date.from(now.minusDays(20).toInstant())
        collection.insertOne(Document("url", "existing")
            .append("lastChecked", Date.from(now.toInstant()))
            .append("lastAccessed", accessed)
            .append("data", "data"))

        val store = InternalDbStoreImpl(mongoClient)
        val document = collection.find(Document("url", "existing")).firstOrNull()!!

        assertEquals(accessed, document.getDate("lastAccessed"))
        assertEquals(1L, store.evictUnusedCache(now.plusDays(11)))
    }
}
