package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.models.GHReleaseMetadata
import org.junit.Assert
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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

    @Test
    fun `metadata is persisted`(apiPersistence: MongoApiPersistence) {
        runBlocking {

            val metadata = GHReleaseMetadata(10, GitHubId("foo"))
            apiPersistence.setGhReleaseMetadata(metadata)

            val saved = apiPersistence.getGhReleaseMetadata(GitHubId("foo"))

            assertEquals(metadata, saved)
        }
    }
}
