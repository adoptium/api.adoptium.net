package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, defaultValue = "all", enumeration = ["github", "dockerhub", "all"], example = "all")
enum class StatsSource {
    github,
    dockerhub,
    all;
}
