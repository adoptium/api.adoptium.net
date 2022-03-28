package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.api.marketplace.parser.maven.VersionRange
import net.adoptium.marketplace.schema.OpenjdkVersionData
import java.util.function.Predicate

class VersionRangeFilter(range: String?) : Predicate<OpenjdkVersionData> {

    private val rangeMatcher: Predicate<OpenjdkVersionData>?

    init {
        rangeMatcher = if (range == null) {
            null
        } else {
            VersionRange.parse(range)
        }
    }

    override fun test(version: OpenjdkVersionData): Boolean {
        return when {
            rangeMatcher != null -> {
                rangeContainsVersion(rangeMatcher, version)
            }
            else -> {
                true
            }
        }
    }

    private fun rangeContainsVersion(rangeMatcher: Predicate<OpenjdkVersionData>, version: OpenjdkVersionData): Boolean {
        val noPreVersion = if (version.pre != null) {
            OpenjdkVersionData(
                version.major,
                version.minor.orElse(null),
                version.security.orElse(null),
                version.patch.orElse(null),
                version.pre.orElse(null),
                version.build.orElse(null),
                version.optional.orElse(null),
                version.openjdk_version,
            )
        } else {
            version
        }

        return rangeMatcher.test(noPreVersion)
    }
}
