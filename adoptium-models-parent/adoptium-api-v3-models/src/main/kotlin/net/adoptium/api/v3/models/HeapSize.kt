package net.adoptium.api.v3.models

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import java.util.*

@Schema(type = SchemaType.STRING, enumeration = ["normal", "large"], example = "normal")
enum class HeapSize : FileNameMatcher {
    normal, large(0, "XL", "LinuxLH");

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(this.name, alternativeNames.toList())
    }

    override fun matchesFile(fileName: String): Boolean {
        val lowerCaseFileName = fileName.lowercase(Locale.getDefault())
        return names
            .firstOrNull {
                lowerCaseFileName.contains(Regex("${it}_"))
            } != null
    }
}
