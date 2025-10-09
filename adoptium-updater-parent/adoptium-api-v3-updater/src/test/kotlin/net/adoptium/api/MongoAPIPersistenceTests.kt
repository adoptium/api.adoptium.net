package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.MongoClient
import net.adoptium.api.v3.models.GHReleaseMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MongoAPIPersistenceTests : MongoTest() {
    @Test
    fun `update time is set`(api: MongoApiPersistence) {
        runBlocking {
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            api.updateUpdatedTime(TimeSource.now(), "", 0)
            val time = TimeSource.now()
            api.updateUpdatedTime(time, "", 0)

            val stored = api.getUpdatedAt()

            assertEquals(time, stored.time)
        }
    }

    @Test
    fun `attestation update time is set`(api: MongoApiPersistence) {
        runBlocking {
            api.updateAttestationUpdatedTime(TimeSource.now(), "", 0)
            api.updateAttestationUpdatedTime(TimeSource.now(), "", 0)
            api.updateAttestationUpdatedTime(TimeSource.now(), "", 0)
            val time = TimeSource.now()
            api.updateAttestationUpdatedTime(time, "", 0)

            val stored = api.getAttestationUpdatedAt()

            assertEquals(time, stored.time)
        }
    }

    @Test
    fun `metadata is persisted`() {
        runBlocking {
            val apiPersistence = MongoApiPersistence(MongoClient())

            val metadata = GHReleaseMetadata(10, GitHubId("foo"))
            apiPersistence.setGhReleaseMetadata(metadata)

            val saved = apiPersistence.getGhReleaseMetadata(GitHubId("foo"))


            assertEquals(metadata, saved)
        }
    }
}
