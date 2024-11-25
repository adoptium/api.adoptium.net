package net.adoptium.api

import net.adoptium.api.v3.ReleaseIncludeFilter
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.slf4j.LoggerFactory

class V3UpdaterTest {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
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
