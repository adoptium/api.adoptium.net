package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import net.adoptium.marketplace.client.TestServer
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.dataSources.persitence.mongo.MongoTest
import net.adoptium.marketplace.server.updater.AdoptiumMarketplaceUpdater
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@QuarkusTest
@EnableAutoWeld
@ExtendWith(value = [TestServer::class])
@AddPackages(value = [APIDataStore::class])
class UpdateRunner {
    @Test
    @Disabled("For Manual Testing")
    fun run(apiDataStore: APIDataStore) {
        MongoTest.startFongo(22222)

        val adoptiumMarketplaceUpdater = AdoptiumMarketplaceUpdater(apiDataStore, MockVendorList())
        adoptiumMarketplaceUpdater.scheduleUpdates()
        Thread.sleep(Long.MAX_VALUE)
    }
}
