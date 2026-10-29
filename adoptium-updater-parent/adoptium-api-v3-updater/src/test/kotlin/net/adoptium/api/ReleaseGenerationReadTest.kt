package net.adoptium.api

import com.mongodb.MongoException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ReleaseGenerationReadTest {
    private val previous = UpdatedInfo(TimeSource.now().minusDays(1), "previous", 1)
    private val empty = AdoptRepos(emptyList())

    @ParameterizedTest
    @CsvSource("complete,incomplete:writing", "complete,new-complete", "incomplete:writing,new-complete")
    fun `unpublished or changing snapshots are rejected even on forced reads`(start: String, end: String) {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returnsMany listOf(
            previous.copy(checksum = start), previous.copy(checksum = end)
        )
        coEvery { database.readReleaseData(8) } returns FeatureRelease(8, emptyList())
        assertThrows(IllegalStateException::class.java) {
            APIDataStoreImpl.loadDataFromDb(database, previous, true, empty, listOf(8), false)
        }
        if (start.startsWith("incomplete:")) coVerify(exactly = 0) { database.readReleaseData(any()) }
    }

    @Test
    fun `incomplete generation is not accepted on startup or as a poll cache hit`() {
        val database = mockk<ApiPersistence>()
        val incomplete = previous.copy(checksum = "incomplete:failed")
        coEvery { database.getUpdatedAt() } returns incomplete
        for (oldRepo in listOf(null, empty)) {
            assertThrows(IllegalStateException::class.java) {
                APIDataStoreImpl.loadDataFromDb(database, incomplete, false, oldRepo, listOf(8), false)
            }
        }
        coVerify(exactly = 0) { database.readReleaseData(any()) }
    }

    @Test
    fun `recovery baseline reads the surviving records of an incomplete generation`() {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returns previous.copy(checksum = "incomplete:failed")
        coEvery { database.readReleaseData(any()) } returns FeatureRelease(8, emptyList())
        APIDataStoreImpl.loadRecoveryBaseline(database)
        coVerify { database.readReleaseData(8) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["mongo", "generation", "cancelled"])
    fun `failed recovery baseline aborts startup before scheduling updates`(kind: String) {
        val failure = when (kind) {
            "mongo" -> MongoException("Recovery read failed")
            "generation" -> IllegalStateException("Generation changed during recovery")
            else -> CancellationException("Recovery cancelled")
        }
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returns previous.copy(checksum = "incomplete:failed")
        coEvery { database.readReleaseData(8) } throws failure
        val store = mockk<APIDataStore>()
        coEvery { store.isConnectedToDb() } returns true
        every { store.loadCdxaDataFromDb(any(), any()) } throws AssertionError("Startup continued without a recovery baseline")
        val builder = mockk<AdoptReposBuilder>()
        val updater = V3Updater(builder, mockk(), store, database, mockk(), mockk(), mockk(), mockk())

        assertSame(failure, assertThrows(Exception::class.java) { updater.run(true) })

        coVerify(exactly = 0) {
            database.updateAllRepos(any(), any())
            builder.build(any())
        }
        verify(exactly = 0) { store.loadCdxaDataFromDb(any(), any()) }
    }

    @Test
    fun `rejected read preserves cached generation and a later successful poll reloads`() = runBlocking {
        val database = InMemoryApiPersistence(empty, AdoptCdxaRepos(emptyList()))
        database.updateAllRepos(empty, "published")
        val store = APIDataStoreImpl(database)
        val cached = store.getAdoptRepos()
        val generation = store.getUpdateInfo()
        database.updateAllRepos(empty, "incomplete:failed")
        assertThrows(IllegalStateException::class.java) { store.loadDataFromDb(true) }
        assertSame(cached, store.getAdoptRepos())
        assertEquals(generation, store.getUpdateInfo())
        database.updateAllRepos(empty, "recovered")
        store.loadDataFromDb(false)
        assertEquals("recovered", store.getUpdateInfo().checksum)
    }

    @Test
    fun `cancelled snapshot reads propagate cancellation`() {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returns previous
        coEvery { database.readReleaseData(8) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            APIDataStoreImpl.loadDataFromDb(database, previous, true, null, listOf(8), false)
        }
    }
}
