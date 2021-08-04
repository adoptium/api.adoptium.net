package net.adoptopenjdk.api.v3.models

object VendorSchemaRef {
    fun getDefault(): String {
        return AdoptiumVendor.DEFAULT_VALUE
    }

    fun getValidVendors(): List<String> {
        return AdoptiumVendor.VENDOR_VALUES.toList()
    }

    fun applyVendorMapping(vendor: String): String {
        return if (AdoptiumVendor.VENDOR_MAPPING.containsKey(vendor)) {
            AdoptiumVendor.VENDOR_MAPPING[vendor]!!
        } else {
            vendor
        }
    }

    const val SCHEMA_REF = "#/components/schemas/AdoptiumVendor"
}
