package net.adoptium.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.Binary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.LoggerFactory
import java.util.*

class APIDataStoreTest : MongoTest() {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @Test
    fun reposHasElements() {
        runBlocking {
            val repo = BaseTest.adoptRepos
            assert(repo.getFeatureRelease(8)!!.releases.getReleases().toList().isNotEmpty())
        }
    }

    @Test
    fun `can deserialize binaries`() {
        val binary = BaseTest.adoptRepos.allReleases
            .getReleases()
            .first()
            .binaries
            .first()
        val str = JsonMapper.mapper.writeValueAsString(binary)

        val read = JsonMapper.mapper.readValue(str, Binary::class.java)
        assertEquals(binary, read)
    }

    @Test
    fun dataIsStoredToDbCorrectly(apiDataStore: APIDataStore, apiPersistence: ApiPersistence) {
        runBlocking {
            apiPersistence.updateAllRepos(BaseTest.adoptRepos, "")
            val dbData = apiDataStore.loadDataFromDb(false)

            val before = UpdaterJsonMapper.mapper.writeValueAsString(dbData)
            val after = UpdaterJsonMapper.mapper.writeValueAsString(BaseTest.adoptRepos)
            JSONAssert.assertEquals(
                before,
                after,
                true
            )
        }
    }

    @Test
    fun attestationDataIsStoredToDbCorrectly(apiDataStore: APIDataStore, apiPersistence: ApiPersistence) {
        runBlocking {
            apiPersistence.updateAttestationRepos(BaseTest.adoptAttestationRepos, "")
            val dbData = apiDataStore.loadAttestationDataFromDb(false)

            val before = UpdaterJsonMapper.mapper.writeValueAsString(dbData)
            val after = UpdaterJsonMapper.mapper.writeValueAsString(BaseTest.adoptAttestationRepos)
            JSONAssert.assertEquals(
                before,
                after,
                true
            )
        }
    }

    @Test
    fun `updated at is set`(apiPersistence: ApiPersistence) {
        runBlocking {
            apiPersistence.updateAllRepos(BaseTest.adoptRepos, Base64.getEncoder().encodeToString("1234".toByteArray()))
            val time = TimeSource.now()
            delay(1000)
            apiPersistence.updateAllRepos(BaseTest.adoptRepos, Base64.getEncoder().encodeToString("a-checksum".toByteArray()))

            val updatedTime = apiPersistence.getUpdatedAt()

            assertTrue(updatedTime.time.isAfter(time))
            assertEquals(Base64.getEncoder().encodeToString("a-checksum".toByteArray()), updatedTime.checksum)
        }
    }

    @Test
    fun `attestation updated at is set`(apiPersistence: ApiPersistence) {
        runBlocking {
            apiPersistence.updateAttestationRepos(BaseTest.adoptAttestationRepos, Base64.getEncoder().encodeToString("1234".toByteArray()))
            val time = TimeSource.now()
            delay(1000)
            apiPersistence.updateAttestationRepos(BaseTest.adoptAttestationRepos, Base64.getEncoder().encodeToString("a-checksum".toByteArray()))

            val updatedTime = apiPersistence.getAttestationUpdatedAt()

            assertTrue(updatedTime.time.isAfter(time))
            assertEquals(Base64.getEncoder().encodeToString("a-checksum".toByteArray()), updatedTime.checksum)
        }
    }

    @Test
    fun `update is not scheduled by default`(apiDataStore: APIDataStoreImpl) {
        assertNull(apiDataStore.getSchedule())
    }
}
