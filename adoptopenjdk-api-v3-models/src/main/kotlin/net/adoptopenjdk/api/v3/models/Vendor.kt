package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING,
    defaultValue = "adoptopenjdk",
    enumeration = ["adoptopenjdk", "openjdk"],
    example = "adoptopenjdk"
)
enum class Vendor {
    adoptopenjdk, openjdk
    // , amazon, azul, bellsoft, , sap;
}
