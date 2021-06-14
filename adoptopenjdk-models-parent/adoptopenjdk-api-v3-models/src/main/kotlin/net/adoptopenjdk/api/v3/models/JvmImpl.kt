package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

/*
    Use a schema ref as adopt and adoptium will have a different subset of implementations
*/
@Schema(ref = JvmImplSchemaRef.SCHEMA_REF)
enum class JvmImpl : FileNameMatcher {
    hotspot, openj9, dragonwell;

    override lateinit var names: List<String>
    override var priority: Int = 0

    constructor(priority: Int = 0, vararg alternativeNames: String) {
        this.priority = priority
        setNames(this.name, alternativeNames.toList())
    }

    companion object {

        private val VALID_JVM_IMPL = JvmImplSchemaRef
            .VALID_JVM_IMPL_VALUES
            .map { valueOf(it) }
            .toSet()

        fun validJvmImpl(jvmImpl: JvmImpl): Boolean {
            return VALID_JVM_IMPL.contains(jvmImpl)
        }

        fun assertValidJvmImpl(jvmImpl: JvmImpl): JvmImpl {
            return if (!validJvmImpl(jvmImpl)) {
                throw IllegalArgumentException("${jvmImpl.name} is not a valid jvmImpl value")
            } else {
                jvmImpl
            }
        }

        /**
         * Use to double check that we will not accept a JvmImpl that is not available for this ecosystem
         */
        fun getValue(value: String): JvmImpl {
            return assertValidJvmImpl(valueOf(value))
        }
    }
}
