package net.adoptium.marketplace.server.updater

import net.adoptium.marketplace.schema.Vendor
import org.slf4j.LoggerFactory
import javax.inject.Singleton

interface VendorList {
    fun getVendorInfo(): Map<Vendor, VendorInfo>
}

@Singleton
class DefaultVendorList : VendorList {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)


        val VENDORS = Vendor
            .values()
            .map {
                LOGGER.info("Loading config for ${it}")
                it to VendorInfo(it)
            }
            .filter { it.second.valid() }
            .toMap()
    }

    init {
        VENDORS
            .forEach {
                LOGGER.info("Loaded config for ${it.key}, ${it.value.getUrl()} ${it.value.getKey()}")
            }
    }

    override fun getVendorInfo(): Map<Vendor, VendorInfo> {
        return VENDORS
    }
}
