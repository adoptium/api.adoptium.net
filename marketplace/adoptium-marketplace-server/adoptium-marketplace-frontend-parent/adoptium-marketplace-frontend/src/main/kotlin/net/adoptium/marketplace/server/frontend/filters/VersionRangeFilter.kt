package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.api.marketplace.parser.maven.VersionRange
import net.adoptium.marketplace.schema.OpenjdkVersionData
import net.adoptium.marketplace.server.frontend.versions.VersionParser
import java.util.function.Predicate

class VersionRangeFilter(range: String?) : Predicate<OpenjdkVersionData> {

    private val rangeMatcher: VersionRange?
    private val exactMatcher: OpenjdkVersionData?

    init {
        if (range == null) {
            rangeMatcher = null
            exactMatcher = null
        } else if (
            !range.startsWith('(') &&
            !range.startsWith('[') &&
            !range.endsWith(')') &&
            !range.endsWith(']')
        ) {
            rangeMatcher = null
            exactMatcher = VersionParser.parse(range, sanityCheck = false, exactMatch = true)
        } else {
            rangeMatcher = VersionRange.createFromVersionSpec(range)
            exactMatcher = null
        }
    }

    override fun test(version: OpenjdkVersionData): Boolean {
        return when {
            exactMatcher != null -> {
                exactMatcher.compareTo(version) == 0
            }
            rangeMatcher != null -> {
                rangeContainsVersion(rangeMatcher, version)
            }
            else -> {
                true
            }
        }
    }

    private fun rangeContainsVersion(rangeMatcher: VersionRange, version: OpenjdkVersionData): Boolean {
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

        return rangeMatcher.containsVersion(noPreVersion)
    }
}
