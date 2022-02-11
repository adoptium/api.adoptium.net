package net.adoptium.marketplace.dataSources.persitence.mongo

import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.schema.Vendor
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Test


@EnableAutoWeld
class PersistenceTest : MongoTest() {

    @Test
    fun `can write then read back data`(apiDataStore: APIDataStore) {
        apiDataStore.getReleases(Vendor.adoptium)
    }
}
