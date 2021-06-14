package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import org.junit.Assert
import org.junit.jupiter.api.Test

class MongoAPIPersistenceTests : MongoTest() {
    @Test
    fun `update time is set`(apiPersistence: MongoApiPersistence) {
        runBlocking {
            val api = apiPersistence
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            val time = TimeSource.now()
            api.updateUpdatedTime(time, "", 0)

            val stored = api.getUpdatedAt()

            Assert.assertEquals(time, stored.time)
        }
    }
}
