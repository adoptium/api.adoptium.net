package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, defaultValue = "ga", enumeration = ["ga", "ea"])
enum class ReleaseType {
    ga,
    ea;
}
