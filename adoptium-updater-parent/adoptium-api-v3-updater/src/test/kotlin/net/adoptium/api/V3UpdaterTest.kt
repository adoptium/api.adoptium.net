package net.adoptium.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.quarkus.runtime.ApplicationLifecycleManager
import io.quarkus.runtime.Quarkus
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.ReleaseIncludeFilter
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class V3UpdaterTest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    @Test
    fun `exit is called when db not present`() {
        runBlocking {
            val apiDataStore: APIDataStore = mockk()
            coEvery { apiDataStore.isConnectedToDb() } returns false

            mockkStatic(Quarkus::class)
            val called = AtomicBoolean(false)
            every { Quarkus.asyncExit(any()) } answers {
                called.set(true)
                ApplicationLifecycleManager.exit(2)
            }

            val updater = V3Updater(
                mockk(),
                apiDataStore,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk()
            )

            updater.run(true)
            assertTrue(called.get())
        }
    }

    @Test
    fun `checksum works`() {
        runBlocking {
            val checksum = V3Updater.calculateChecksum(BaseTest.adoptRepos)
            assertTrue(checksum.length == 44)
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "adoptopenjdktests", matches = "true")
    fun `adoptOpenJdk releases are copied over`() {

        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate()

            val filter = ReleaseIncludeFilter(
                TimeSource.now(),
                ReleaseFilterType.ALL,
                false,
                setOf(Vendor.adoptopenjdk)
            )

            val adoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { filter.filter(it.vendor, it.updated_at.dateTime, it.release_type == ReleaseType.ea) }
                .toList()
            )

            val notAdoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { !filter.filter(it.vendor, it.updated_at.dateTime, it.release_type == ReleaseType.ea) }
                .toList()
            )

            val concated = V3Updater.copyOldReleasesIntoNewRepo(adoptopenjdk, notAdoptopenjdk, filter)

            Assertions.assertEquals(repo, concated)
        }
    }
}
