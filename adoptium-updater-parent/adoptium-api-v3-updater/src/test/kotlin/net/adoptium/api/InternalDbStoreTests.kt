package net.adoptium.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import net.adoptium.api.testDoubles.InMemoryInternalDbStore
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.mongo.InternalDbStore
import net.adoptium.api.v3.dataSources.mongo.InternalDbStoreImpl
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.Date

class InternalDbStoreTests : MongoTest() {

    @Test
    fun `usage is a BSON date with a thirty day TTL and one-time legacy grace`() = runBlocking {
        val client = MongoClient()
        val raw = client.getDatabase().getCollection<Document>("web-cache")
        raw.deleteMany(Document())
        val existing = TimeSource.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS)
        for (url in listOf("missing", "null", "existing")) {
            val entry = Document("url", url).append("data", "body")
                .append("lastChecked", Date.from(existing.minusYears(1).toInstant()))
            if (url == "null") entry.append("lastAccessed", null)
            if (url == "existing") entry.append("lastAccessed", Date.from(existing.toInstant()))
            raw.insertOne(entry)
        }
        val before = TimeSource.now().toInstant().toEpochMilli()
        val store = InternalDbStoreImpl(client)
        val after = TimeSource.now().toInstant().toEpochMilli()
        val migrated = raw.find().toList().associateBy { it.getString("url") }
        for (url in listOf("missing", "null")) {
            assertTrue(migrated.getValue(url).getDate("lastAccessed").time in before..after)
            assertEquals("body", migrated.getValue(url).getString("data"))
            assertEquals(Date.from(existing.minusYears(1).toInstant()), migrated.getValue(url).getDate("lastChecked"))
        }
        assertEquals(Date.from(existing.toInstant()), migrated.getValue("existing").getDate("lastAccessed"))
        InternalDbStoreImpl(client)
        assertEquals(migrated, raw.find().toList().associateBy { it.getString("url") })
        val ttl = raw.listIndexes().toList().single { it.get("key", Document::class.java).containsKey("lastAccessed") }
        assertEquals(Document("lastAccessed", 1), ttl["key"])
        assertEquals(30L * 24 * 60 * 60, (ttl["expireAfterSeconds"] as Number).toLong())
        store.putCachedWebpage("new", null, existing, "new body").join()
        assertTrue(raw.find(Document("url", "new")).first()["lastAccessed"] is Date)
    }

    @Test
    fun `Mongo content and validation updates preserve usage atomically`() = runBlocking {
        val client = MongoClient()
        val raw = client.getDatabase().getCollection<Document>("web-cache")
        raw.deleteMany(Document())
        val store = InternalDbStoreImpl(client)
        val usage = TimeSource.now().plusDays(1).truncatedTo(ChronoUnit.MILLIS)
        store.putCachedWebpage("foo", "old", usage.minusDays(3), "old", usage).join()
        store.putCachedWebpage("foo", "new", usage, "new", accessedAt = null).join()
        store.updateCheckedTime("foo", usage.plusDays(1))
        val entry = raw.find(Document("url", "foo")).first()
        assertEquals(Date.from(usage.toInstant()), entry.getDate("lastAccessed"))
        assertEquals(Date.from(usage.plusDays(1).toInstant()), entry.getDate("lastChecked"))
        assertEquals("new", entry.getString("lastModified"))
        assertEquals("new", entry.getString("data"))
        assertFalse(entry.containsKey("accessedAt"))
    }

    @Test
    fun `Mongo and in-memory stores retain demand but not background usage`() = runBlocking {
        val client = MongoClient()
        client.getDatabase().getCollection<Document>("web-cache").deleteMany(Document())
        for (store in listOf<InternalDbStore>(InternalDbStoreImpl(client), InMemoryInternalDbStore())) {
            val now = TimeSource.now().truncatedTo(ChronoUnit.MILLIS)
            val cutoff = now.minusDays(30)
            store.putCachedWebpage("at-cutoff", null, now, "body", cutoff).join()
            store.putCachedWebpage("before-cutoff", null, now, "body", cutoff.minusNanos(1_000_000)).join()
            store.putCachedWebpage("after-cutoff", null, now, "body", cutoff.plusNanos(1_000_000)).join()
            assertEquals(2L, store.evictUnusedCache(cutoff))
            assertNotNull(store.getCachedWebpage("after-cutoff"))

            store.putCachedWebpage("read", "modified", now, "body", now.minusDays(31)).join()
            val read = store.getCachedWebpage("read")!!
            assertTrue(!read.lastAccessed!!.isBefore(now))
            assertEquals(now.toInstant(), read.lastChecked!!.toInstant())
            assertEquals("body", read.data)
            assertEquals(0L, store.evictUnusedCache(cutoff))

            // A delayed demand write and a clock moving backwards cannot undo newer use.
            val future = now.plusDays(1)
            store.putCachedWebpage("demand", null, now, "body", future).join()
            store.putCachedWebpage("demand", null, now, "delayed", cutoff.minusDays(1)).join()
            assertEquals(future.toInstant(), store.getCachedWebpage("demand")!!.lastAccessed!!.toInstant())
            assertEquals(0L, store.evictUnusedCache(now.minusSeconds(1)))

            store.putCachedWebpage("background200", null, now, "old", cutoff).join()
            store.putCachedWebpage("background200", "new", now, "new", accessedAt = null).join()
            store.putCachedWebpage("background304", null, now, "body", cutoff).join()
            store.updateCheckedTime("background304", now)
            assertEquals(2L, store.evictUnusedCache(cutoff))

            // Direct uncached demand writes and background insertions both initialize usage now,
            // not at an ancient validation timestamp.
            store.putCachedWebpage("direct", null, cutoff.minusDays(1), "body").join()
            store.putCachedWebpage("background-insert", null, cutoff.minusDays(1), "body", accessedAt = null).join()
            assertEquals(0L, store.evictUnusedCache(cutoff))
            assertTrue(!store.getCachedWebpage("direct")!!.lastAccessed!!.isBefore(now))
            assertTrue(!store.getCachedWebpage("background-insert")!!.lastAccessed!!.isBefore(now))
        }
    }
}
