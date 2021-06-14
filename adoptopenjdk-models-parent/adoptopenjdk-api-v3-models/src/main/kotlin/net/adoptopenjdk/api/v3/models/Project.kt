package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

@Schema(
    type = SchemaType.STRING,
    name = "project",
    description = "Project",
    defaultValue = "jdk",
    enumeration = ["jdk", "valhalla", "metropolis", "jfr", "shenandoah"],
    required = false
)
enum class Project : FileNameMatcher {
    jdk,
    valhalla,
    metropolis,
    jfr(1),
    shenandoah(1);

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(this.name, alternativeNames.toList())
    }
}
