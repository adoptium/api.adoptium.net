package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, enumeration = ["hotspot"])
class AdoptiumJvmImpl {
    companion object {
        // Duplicate of above array as we cannot referece this in an annotation, keep these lists in sync
        val JVM_IMPL_VALUES = arrayOf("hotspot")
    }
}
