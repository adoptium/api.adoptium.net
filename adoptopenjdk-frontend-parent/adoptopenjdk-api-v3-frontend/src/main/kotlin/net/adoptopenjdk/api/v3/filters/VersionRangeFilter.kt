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
                rangeMatcher.containsVersion(version)
            }
            else -> {
                true
            }
        }
    }
}
