package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.V3
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertNotNull

@ExtendWith(value = [DbExtension::class])
class V3Test : FrontendTest() {

    @Test
    fun `update is scheduled`(apiDataStore: APIDataStore) {
        System.setProperty(V3.ENABLE_PERIODIC_UPDATES, "true")
        V3().schedulePeriodicUpdates(apiDataStore)
        assertNotNull((apiDataStore as ApiDataStoreStub).scheduled)
    }
}
