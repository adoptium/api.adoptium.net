package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

/*
    Use a schema ref as adopt and adoptium will have a different subset of vendors
*/
@Schema(ref = VendorSchemaRef.SCHEMA_REF)
enum class Vendor {
    adoptium, adoptopenjdk, openjdk, alibaba, ibm, eclipse;
    // , amazon, azul, bellsoft, , sap;

    companion object {

        // Although the Vendor enum contains all possible values, only a subset will be valid for the current deployment
        // Use the ecosystem specific VendorSchemaRef, class to determine the valid values
        val VALID_VENDORS: Set<Vendor> = VendorSchemaRef
            .getValidVendors()
            .map { valueOf(it) }
            .toSet()

        fun validVendor(vendor: Vendor): Boolean {
            return VALID_VENDORS.contains(vendor)
        }

        fun assertValidVendor(vendor: Vendor): Vendor {
            return if (!validVendor(vendor)) {
                throw IllegalArgumentException("${vendor.name} is not a valid vendor value")
            } else {
                vendor
            }
        }

        fun getDefault(): Vendor {
            return valueOf(VendorSchemaRef.getDefault())
        }

        /**
         * Use to double check that we will not accept a vendor that is not available for this ecosystem
         */
        fun getValue(value: String): Vendor {
            return assertValidVendor(valueOf(VendorSchemaRef.applyVendorMapping(value)))
        }
    }
}
