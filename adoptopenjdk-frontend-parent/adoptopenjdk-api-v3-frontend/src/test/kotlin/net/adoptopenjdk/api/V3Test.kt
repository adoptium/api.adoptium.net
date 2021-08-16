package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.Startup
import net.adoptopenjdk.api.v3.Startup.Companion.ENABLE_PERIODIC_UPDATES
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertNotNull

@ExtendWith(value = [DbExtension::class])
class V3Test : FrontendTest() {

    @Test
    fun `update is scheduled`(apiDataStore: APIDataStore) {
        System.setProperty(ENABLE_PERIODIC_UPDATES, "true")
        Startup(apiDataStore).schedulePeriodicUpdates()
        assertNotNull((apiDataStore as ApiDataStoreStub).scheduled)
    }
}
