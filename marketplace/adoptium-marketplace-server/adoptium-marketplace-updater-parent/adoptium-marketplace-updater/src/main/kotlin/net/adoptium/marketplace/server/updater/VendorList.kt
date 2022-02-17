package net.adoptium.marketplace.server.updater

import net.adoptium.marketplace.schema.Vendor
import javax.inject.Singleton

interface VendorList {
    fun getVendorInfo(): Map<Vendor, VendorInfo>
}

@Singleton
class DefaultVendorList : VendorList {

    companion object {
        val VENDORS = mapOf(
            Vendor.adoptium to VendorInfo(Vendor.adoptium, "http://localhost:8090", "adoptium.pub")
        )
    }

    override fun getVendorInfo(): Map<Vendor, VendorInfo> {
        return VENDORS
    }
}
