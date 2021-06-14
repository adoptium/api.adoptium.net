package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, enumeration = ["linux", "windows", "mac", "solaris", "aix", "alpine-linux"])
enum class OperatingSystem : FileNameMatcher {
    linux(0, "LinuxLH"),
    windows(0, "win"),
    mac,
    solaris,
    aix,
    `alpine-linux`;

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(this.name, alternativeNames.toList())
    }
}
