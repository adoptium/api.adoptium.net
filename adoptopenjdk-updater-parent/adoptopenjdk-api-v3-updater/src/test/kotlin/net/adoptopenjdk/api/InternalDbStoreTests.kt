package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.mongo.CacheDbEntry
import net.adoptopenjdk.api.v3.dataSources.mongo.InternalDbStoreImpl
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.MongoClient
import org.junit.Assert
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InternalDbStoreTests : MongoTest() {

    @Test
    fun `checked time is set`(mongoClient: MongoClient) {
        runBlocking {
            val internalDbStore = InternalDbStoreImpl(mongoClient)

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

            Assert.assertEquals(CacheDbEntry(data.url, data.lastModified, newTime, data.data), updated)
        }
    }
}
