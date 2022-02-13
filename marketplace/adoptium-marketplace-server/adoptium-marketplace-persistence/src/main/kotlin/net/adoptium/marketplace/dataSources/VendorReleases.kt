package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.dataSources.persitence.VendorPersistence
import net.adoptium.marketplace.dataSources.persitence.VendorPersistenceFactory
import net.adoptium.marketplace.schema.ReleaseList
import net.adoptium.marketplace.schema.Vendor
import java.time.Duration
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
    private var lastCheck: ZonedDateTime? = null

    companion object {
        var UPDATE_COOLOFF_IN_SECONDS = 60
    }

    suspend fun getAllReleases(): ReleaseList {
        releaseList = if (releaseList == null) {
            lastCheck = TimeSource.now()
            vendorPersistence.getAllReleases()
        } else {
            if (Duration.between(lastCheck, TimeSource.now()).seconds > UPDATE_COOLOFF_IN_SECONDS) {
                lastCheck = TimeSource.now()
                val updated = vendorPersistence.getUpdatedInfoIfUpdatedSince(lastUpdated!!)
                if (updated != null) {
                    // Possible race condition here
                    lastUpdated = updated.time
                    vendorPersistence.getAllReleases()
                } else {
                    releaseList
                }
            } else {
                vendorPersistence.getAllReleases()
            }
        }

        return releaseList!!
    }

    suspend fun writeReleases(releaseList: ReleaseList): ReleaseList {
        return vendorPersistence.writeReleases(releaseList)
    }
}
