package net.adoptium.api

import net.adoptium.api.v3.Startup
import net.adoptium.api.v3.Startup.Companion.ENABLE_PERIODIC_UPDATES
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@AddPackages(value=[ApiDataStoreStub::class])
@ExtendWith(value = [DbExtension::class])
class V3Test : FrontendTest() {

    @Test
    fun `update is scheduled`(apiDataStore: ApiDataStoreStub) {
        System.setProperty(ENABLE_PERIODIC_UPDATES, "true")
        Startup(apiDataStore).schedulePeriodicUpdates()
        assertNotNull((apiDataStore).scheduled)
    }
}
