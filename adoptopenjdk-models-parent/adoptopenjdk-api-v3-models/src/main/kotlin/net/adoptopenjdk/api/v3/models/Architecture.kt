package net.adoptopenjdk.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema

// note x86 does not exist as an enum entry, however for the case of front end queries we make x86==x32
@Schema(type = SchemaType.STRING, enumeration = ["x64", "x86", "x32", "ppc64", "ppc64le", "s390x", "aarch64", "arm", "sparcv9", "riscv64"])
enum class Architecture : FileNameMatcher {
    x64,
    x32(0, "x86-32"),
    ppc64,
    ppc64le,
    s390x,
    aarch64,
    arm(0, "arm32"),
    sparcv9,
    riscv64
    ;

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(this.name, alternativeNames.toList())
    }

    companion object {

        @JvmStatic
        @JsonCreator
        fun forValue(value: String): Architecture {
            return values()
                .filter { it.names.contains(value) }
                .first()
        }

        fun getValue(value: String): Architecture {
            // For frontend queries make x86 == x32
            return if (value == "x86") {
                x32
            } else {
                valueOf(value)
            }
        }
    }
}
