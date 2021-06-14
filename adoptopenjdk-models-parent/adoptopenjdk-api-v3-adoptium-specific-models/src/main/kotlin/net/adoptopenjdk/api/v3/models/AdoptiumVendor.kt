package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING,
    defaultValue = AdoptiumVendor.DEFAULT_VALUE,
    enumeration = ["adoptium"],
    example = "adoptium"
)
class AdoptiumVendor {
    companion object {
        const val DEFAULT_VALUE = "adoptium"

        // Duplicate of above array as we cannot referece this in an annotation, keep these lists in sync
        val VENDOR_VALUES = arrayOf("adoptium")
    }
}
