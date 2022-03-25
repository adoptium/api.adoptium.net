package net.adoptium.marketplace.dataSources

import net.adoptium.marketplace.schema.Vendor
import javax.inject.Inject
import javax.inject.Singleton

interface APIDataStore {
    fun getReleases(vendor: Vendor): VendorReleases
}

@Singleton
class APIDataStoreImpl @Inject constructor(private val vendorReleasesFactory: VendorReleasesFactory) : APIDataStore {

    private val vendorReleases: MutableMap<Vendor, VendorReleases> = mutableMapOf()

    private fun getVendorReleases(vendor: Vendor): VendorReleases {
        if (!vendorReleases.containsKey(vendor)) {
            vendorReleases[vendor] = vendorReleasesFactory.get(vendor)
        }

        return vendorReleases[vendor]!!
    }

    override fun getReleases(vendor: Vendor): VendorReleases {
        return getVendorReleases(vendor)
    }
}
