package net.adoptium.api

import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
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
    fun `background content replacement preserves usage in both stores`() = runBlocking {
        for (store in listOf(InternalDbStoreImpl(mongoClient), InMemoryInternalDbStore())) {
            val now = TimeSource.now()
            val old = now.minusDays(20)
            store.putCachedWebpage("background-200", "old", old, "old body").join()
            store.putCachedWebpage("background-200", "new", now, "new body").join()

            assertEquals(0L, store.evictUnusedCache(old.plusDays(30).minusSeconds(1)))
            assertEquals(1L, store.evictUnusedCache(old.plusDays(30)))
        }
    }

    @Test
    fun `delayed content writes cannot overwrite newer access in either store`() = runBlocking {
        for (store in listOf(InternalDbStoreImpl(mongoClient), InMemoryInternalDbStore())) {
            val old = TimeSource.now().minusDays(20)
            store.putCachedWebpage("delayed", null, old, "old").join()
            val accessed = store.getCachedWebpage("delayed")!!.lastAccessed!!

            // Apply an older demand write and then a background write after the read.
            store.putCachedWebpage("delayed", "demand", old.plusDays(1), "demand", old.plusDays(1)).join()
            store.putCachedWebpage("delayed", "background", old.plusDays(2), "background").join()

            assertEquals(0L, store.evictUnusedCache(accessed.plusDays(30).minusSeconds(1)))
            val entry = store.getCachedWebpage("delayed")!!
            assertTrue(!entry.lastAccessed!!.isBefore(accessed))
            assertEquals("background", entry.data)
            assertEquals("background", entry.lastModified)
            assertEquals(old.plusDays(2), entry.lastChecked)
        }
    }

    @Test
    fun `demand writes advance access without a cache read in both stores`() = runBlocking {
        for (store in listOf(InternalDbStoreImpl(mongoClient), InMemoryInternalDbStore())) {
            val now = TimeSource.now()
            store.putCachedWebpage("direct-demand", null, now.minusDays(20), "old").join()
            store.putCachedWebpage("direct-demand", null, now, "new", now).join()
            assertEquals(0L, store.evictUnusedCache(now.plusDays(29)))
            assertEquals(1L, store.evictUnusedCache(now.plusDays(30)))
        }
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
    fun `legacy missing and null usage get grace only once without changing content`() = runBlocking {
        val old = TimeSource.now().minusYears(1)
        for (url in listOf("missing", "null")) {
            val entry = Document("url", url).append("data", "body")
                .append("lastChecked", Date.from(old.toInstant()))
            if (url == "null") entry.append("lastAccessed", null)
            collection.insertOne(entry)
        }
        val before = TimeSource.now().toInstant().toEpochMilli()
        InternalDbStoreImpl(mongoClient)
        val after = TimeSource.now().toInstant().toEpochMilli()
        val migrated = collection.find().toList().associateBy { it.getString("url") }
        for (url in listOf("missing", "null")) {
            val entry = migrated.getValue(url)
            assertTrue(entry.getDate("lastAccessed").time in before..after)
            assertEquals("body", entry.getString("data"))
            assertEquals(Date.from(old.toInstant()), entry.getDate("lastChecked"))
        }
        InternalDbStoreImpl(mongoClient)
        assertEquals(migrated, collection.find().toList().associateBy { it.getString("url") })
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
