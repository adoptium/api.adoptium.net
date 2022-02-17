package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.api.marketplace.parser.maven.VersionRange
import net.adoptium.marketplace.schema.VersionData
import net.adoptium.marketplace.server.frontend.versions.VersionParser
import java.util.function.Predicate

class VersionRangeFilter(range: String?) : Predicate<VersionData> {

    private val rangeMatcher: VersionRange?
    private val exactMatcher: VersionData?

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

    override fun test(version: VersionData): Boolean {
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

    private fun rangeContainsVersion(rangeMatcher: VersionRange, version: VersionData): Boolean {
        val noPreVersion = if (version.pre != null) {
            // Special case where since pre-releases are less than full releases, 17.0.0-beta is less than 17.0.0
            // this means that if asking for a filter of 16.0.0 < version < 17.0.0, someone would expect this to
            // return ONLY 16 versions, however since 17.0.0-beta < 17.0.0, 17.0.0-beta will be included. This
            // although possibly technically correct is not what users would expect. As a solution strip out
            // pre when performing a range match
            VersionData(
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
