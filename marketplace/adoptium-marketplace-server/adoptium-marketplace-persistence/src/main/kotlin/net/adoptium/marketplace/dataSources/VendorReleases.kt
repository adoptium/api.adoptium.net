package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.dataSources.persitence.VendorPersistence
import net.adoptium.marketplace.dataSources.persitence.VendorPersistenceFactory
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.Vendor
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VendorReleasesFactory @Inject constructor(
    private val vendorPersistenceFactory: VendorPersistenceFactory
) {
    fun get(vendor: Vendor): VendorReleases {
        return VendorReleases(vendorPersistenceFactory.get(vendor))
    }
}

class VendorReleases constructor(
    private val vendorPersistence: VendorPersistence
) {
    private var releaseList: ReleaseList? = null
    private var lastUpdated: ZonedDateTime? = null

    suspend fun getAllReleases(): ReleaseList {
        releaseList = if (releaseList == null) {
            vendorPersistence.getAllReleases()
        } else {
            val updateTime = vendorPersistence.getUpdatedAt().time

            if (vendorPersistence.getUpdatedAt().time != lastUpdated) {
                // Possible race condition here
                lastUpdated = updateTime
                vendorPersistence.getAllReleases()
            } else {
                releaseList
            }
        }

        return releaseList!!
    }
}
