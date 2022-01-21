package net.adoptium.api

import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.V3Updater
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.junit.jupiter.api.Assertions.assertTrue

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
}
