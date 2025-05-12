package net.adoptium.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(type = SchemaType.STRING, enumeration = ["jdk", "jre", "testimage", "debugimage", "staticlibs", "sources", "sbom", "jmods"], example = "jdk")
enum class ImageType : FileNameMatcher {
    jdk,
    jre(1),
    testimage(1),
    debugimage(1),
    staticlibs(1, "static-libs"),
    sources(1, "sources"),
    sbom(1),
    jmods(1);

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
