package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.dataSources.models.AdoptRepos
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
            assertTrue(checksum.length == 24)
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "adoptopenjdktests", matches = "true")
    fun `adoptOpenJdk releases are copied over`() {

        runBlocking {
            val repo = AdoptReposTestDataGenerator.generate();

            val adoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { it.vendor == Vendor.adoptopenjdk }
                .toList()
            )

            val notAdoptopenjdk = AdoptRepos(emptyList()).addAll(repo
                .allReleases
                .getReleases()
                .filter { it.vendor != Vendor.adoptopenjdk }
                .toList()
            )

            val concated = V3Updater.copyOldReleasesIntoNewRepo(adoptopenjdk, notAdoptopenjdk)

            Assertions.assertEquals(repo, concated)
        }
    }
}
