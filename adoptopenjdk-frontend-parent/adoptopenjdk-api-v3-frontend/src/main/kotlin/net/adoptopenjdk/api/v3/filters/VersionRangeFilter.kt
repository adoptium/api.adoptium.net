package net.adoptopenjdk.api.v3.filters

import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.VersionParser
import net.adoptopenjdk.api.v3.parser.maven.VersionRange
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
                exactMatcher.compareVersionNumber(version)
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
                version.minor,
                version.security,
                null,
                version.adopt_build_number,
                version.build,
                version.optional,
                version.openjdk_version,
                version.semver,
                version.patch,
            )
        } else {
            version
        }

        return rangeMatcher.containsVersion(noPreVersion)
    }
}
