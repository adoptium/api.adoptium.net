package net.adoptium.api

import io.quarkus.test.junit.QuarkusTest
import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.updater.AdoptiumMarketplaceUpdater
import net.adoptium.marketplace.server.updater.VendorInfo
import net.adoptium.marketplace.server.updater.VendorList
import org.jboss.weld.junit5.auto.AddPackages
import org.jboss.weld.junit5.auto.EnableAutoWeld
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


@QuarkusTest
@EnableAutoWeld
@AddPackages(value = [APIDataStore::class])
class UpdateRunnerNoDb {
    @Test
    @Disabled("For Manual Testing")
    fun run(apiDataStore: APIDataStore) {
        val adoptiumMarketplaceUpdater = AdoptiumMarketplaceUpdater(apiDataStore, object : VendorList {
            override fun getVendorInfo(): Map<Vendor, VendorInfo> {
                return mapOf(
                    Vendor.adoptium to VendorInfo(Vendor.adoptium, "http://localhost:8090/", "../../../exampleRepositories/keys/public.pem")
                )
            }
        })
        adoptiumMarketplaceUpdater.scheduleUpdates()
        Thread.sleep(Long.MAX_VALUE)
    }
}
