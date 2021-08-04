package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING,
    defaultValue = AdoptiumVendor.DEFAULT_VALUE,
    enumeration = ["eclipse"],
    example = "eclipse"
)
class AdoptiumVendor {
    companion object {
        const val DEFAULT_VALUE = "eclipse"

        // Duplicate of above array as we cannot referece this in an annotation, keep these lists in sync
        val VENDOR_VALUES = arrayOf("eclipse")

        // mapping of vendor renames, i.e rename adoptium to eclipse
        val VENDOR_MAPPING = mapOf(
            "adoptium" to "eclipse"
        )
    }
}
