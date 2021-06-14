package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING,
    defaultValue = AdoptVendor.DEFAULT_VALUE,
    enumeration = ["adoptopenjdk", "openjdk", "adoptium", "alibaba"],
    example = "adoptopenjdk"
)
class AdoptVendor {
    companion object {
        const val DEFAULT_VALUE = "adoptopenjdk"

        // Duplicate of above array as we cannot reference this in an annotation, keep these lists in sync
        val VENDOR_VALUES = arrayOf("adoptopenjdk", "openjdk", "adoptium", "alibaba")
    }
}
