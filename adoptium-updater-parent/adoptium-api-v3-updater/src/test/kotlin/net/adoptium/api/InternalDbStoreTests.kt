package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptium.api.v3.dataSources.mongo.InternalDbStoreImpl
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InternalDbStoreTests : MongoTest() {

    @Test
    fun `checked time is set`() {
        runBlocking {
            val internalDbStore = InternalDbStoreImpl(MongoClient())

            val now = TimeSource.now()
            val data = CacheDbEntry("foo", "bar", now, "some data")
            internalDbStore
                .putCachedWebpage(data.url, data.lastModified, data.lastChecked!!, data.data)
                .join()

            val cachedData = internalDbStore.getCachedWebpage("foo")
            assertEquals(data, cachedData)

            val newTime = TimeSource.now().plusMinutes(1)

            internalDbStore.updateCheckedTime("foo", newTime)
            val updated = internalDbStore.getCachedWebpage("foo")

            assertEquals(CacheDbEntry(data.url, data.lastModified, newTime, data.data), updated)
        }
    }
}
