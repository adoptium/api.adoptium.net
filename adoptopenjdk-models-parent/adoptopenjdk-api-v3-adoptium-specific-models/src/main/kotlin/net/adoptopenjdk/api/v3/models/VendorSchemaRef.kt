package net.adoptopenjdk.api.v3.models

object VendorSchemaRef {
    fun getDefault(): String {
        return AdoptiumVendor.DEFAULT_VALUE
    }

    fun getValidVendors(): List<String> {
        return AdoptiumVendor.VENDOR_VALUES.toList()
    }

    const val SCHEMA_REF = "#/components/schemas/AdoptiumVendor"
}
