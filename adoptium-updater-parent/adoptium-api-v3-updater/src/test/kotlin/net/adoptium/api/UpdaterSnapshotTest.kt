package net.adoptium.api

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.testDoubles.InMemoryApiPersistence
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStoreImpl
import net.adoptium.api.v3.dataSources.UpdatableVersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicBoolean

class UpdaterSnapshotTest {
    private val healthy = BaseTest.adoptRepos.getFeatureRelease(8)!!.releases.nodeList.first {
        it.vendor == Vendor.getDefault() && it.binaries.all { binary -> JvmImpl.validJvmImpl(binary.jvm_impl) }
    }
    private val hidden = Release(
        "internal-zero", healthy.release_type, healthy.release_link, healthy.release_name,
        healthy.timestamp, healthy.updated_at, emptyArray(), 50,
        healthy.vendor, healthy.version_data
    )
    private val before = AdoptRepos(listOf(FeatureRelease(8, Releases(listOf(healthy, hidden)))))

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `updater snapshot preserves internal releases without changing the frontend view`(incomplete: Boolean) = runBlocking {
        val database = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        val checksum = if (incomplete) "incomplete:failed" else V3Updater.calculateChecksum(before)
        database.updateAllRepos(before, checksum)
        if (incomplete) {
            assertThrows(IllegalStateException::class.java) { APIDataStoreImpl.loadUpdaterSnapshot(database) }
        } else {
            val serving = APIDataStoreImpl(database).loadDataFromDb(true)
            assertTrue(serving.allReleases.hasReleaseId(GitHubId(healthy.id)))
            assertFalse(serving.allReleases.hasReleaseId(GitHubId(hidden.id)))
            val snapshot = APIDataStoreImpl.loadUpdaterSnapshot(database)
            assertEquals(checksum, V3Updater.calculateChecksum(snapshot))
            assertEquals(50L, snapshot.allReleases.nodes.getValue(hidden.id).download_count)
        }
        val recovered = APIDataStoreImpl.loadRecoveryBaseline(database)
        assertEquals(V3Updater.calculateChecksum(before), V3Updater.calculateChecksum(recovered))
        assertEquals(50L, recovered.allReleases.nodes.getValue(hidden.id).download_count)
    }

    @Test
    fun `incremental checksum repair retains stored zero-binary releases after a reload`() = runBlocking {
        val database = InMemoryApiPersistence(before, AdoptCdxaRepos(emptyList()))
        database.updateAllRepos(before, V3Updater.calculateChecksum(before))
        val store = APIDataStoreImpl(database)
        val loaded = store.loadDataFromDb(true)
        assertTrue(loaded.allReleases.hasReleaseId(GitHubId(healthy.id)))
        assertFalse(loaded.allReleases.hasReleaseId(GitHubId(hidden.id)))
        val builder = mockk<AdoptReposBuilder>()
        coEvery { builder.build(any()) } throws IllegalStateException("Unrelated full-build fetch failure")
        coEvery { builder.incrementalUpdate(any(), any(), any()) } returns before
        val versions = mockk<UpdatableVersionSupplier>()
        coEvery { versions.updateVersions() } returns Unit
        val updater = V3Updater(builder, mockk(), store, database, mockk(), mockk(), mockk(), versions)

        val after = updater.runUpdate(loaded, AtomicBoolean(true), mockk())

        assertEquals(before, after)
        assertTrue(database.readReleaseData(8).releases.hasReleaseId(GitHubId(hidden.id)))
        assertEquals(50L, database.readReleaseData(8).releases.nodes.getValue(hidden.id).download_count)
    }
}
