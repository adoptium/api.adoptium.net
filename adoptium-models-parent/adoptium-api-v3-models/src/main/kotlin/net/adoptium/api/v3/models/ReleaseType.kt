package net.adoptium.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, defaultValue = "ga", enumeration = ["all", "ga", "ea"])
enum class ReleaseType {
    all,
    ga,
    ea;
}
