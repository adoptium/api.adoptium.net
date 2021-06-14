package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, enumeration = ["jdk", "jre", "testimage", "debugimage", "staticlibs"], example = "jdk")
enum class ImageType : FileNameMatcher {
    jdk,
    jre(1),
    testimage(1),
    debugimage(1),
    staticlibs(1, "static-libs");

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(name, alternativeNames.toList())
    }

    override fun fileNameMatcher(name: String): Regex {
        return Regex("[\\-_]$name[\\-_]")
    }
}
