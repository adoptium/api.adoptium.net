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
import org.junit.jupiter.params.provider.ValueSource

class ReleaseGenerationReadTest {
    private val previous = UpdatedInfo(TimeSource.now().minusDays(1), "previous", 1)
    private val empty = AdoptRepos(emptyList())

    @Test
    fun `changing snapshots are rejected even on forced reads`() {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returnsMany listOf(previous, previous.copy(checksum = "new-complete"))
        coEvery { database.readReleaseData(8) } returns FeatureRelease(8, emptyList())
        assertThrows(IllegalStateException::class.java) {
            APIDataStoreImpl.loadDataFromDb(database, previous, true, empty, listOf(8), false)
        }
    }

    @Test
    fun `updater snapshot rejects generation changes during its complete raw read`() {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returnsMany listOf(previous, previous.copy(checksum = "newer"))
        coEvery { database.readAllReleaseData() } returns empty

        assertThrows(IllegalStateException::class.java) {
            APIDataStoreImpl.loadUpdaterSnapshotWithInfo(database)
        }
        coVerify(exactly = 1) { database.readAllReleaseData() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["mongo", "generation", "cancelled"])
    fun `failed startup snapshot read aborts startup before scheduling updates`(kind: String) {
        val failure = when (kind) {
            "mongo" -> MongoException("Recovery read failed")
            "generation" -> IllegalStateException("Generation changed during recovery")
            else -> CancellationException("Recovery cancelled")
        }
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returns previous
        coEvery { database.readAllReleaseData() } throws failure
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
    fun `cancelled snapshot reads propagate cancellation`() {
        val database = mockk<ApiPersistence>()
        coEvery { database.getUpdatedAt() } returns previous
        coEvery { database.readReleaseData(8) } throws CancellationException("cancelled")
        assertThrows(CancellationException::class.java) {
            APIDataStoreImpl.loadDataFromDb(database, previous, true, null, listOf(8), false)
        }
    }
}
