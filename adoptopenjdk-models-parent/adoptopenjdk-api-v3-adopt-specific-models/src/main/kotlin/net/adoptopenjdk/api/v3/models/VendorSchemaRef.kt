package net.adoptopenjdk.api.v3.models

object VendorSchemaRef {
    fun getDefault(): String {
        return AdoptVendor.DEFAULT_VALUE
    }

    fun getValidVendors(): List<String> {
        return AdoptVendor.VENDOR_VALUES.toList()
    }

    fun applyVendorMapping(vendor: String): String {
        return if (AdoptVendor.VENDOR_MAPPING.containsKey(vendor.toLowerCase())) {
            AdoptVendor.VENDOR_MAPPING[vendor]!!
        } else {
            vendor
        }
    }

    const val SCHEMA_REF = "#/components/schemas/AdoptVendor"
}
