package net.adoptopenjdk.api.v3.models

object VendorSchemaRef {
    fun getDefault(): String {
        return AdoptVendor.DEFAULT_VALUE
    }

    fun getValidVendors(): List<String> {
        return AdoptVendor.VENDOR_VALUES.toList()
    }

    const val SCHEMA_REF = "#/components/schemas/AdoptVendor"
}
