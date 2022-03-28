package net.adoptium.api.marketplace.parser.maven

import net.adoptium.marketplace.schema.OpenjdkVersionData
import net.adoptium.marketplace.server.frontend.versions.VersionParser
import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * Does not support multiple groups i.e (,1.1),(1.1,)
 */
class VersionRange {

    companion object {
        private val MIN_VERSION = OpenjdkVersionData(
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            null
        )

        private val MAX_VERSION = OpenjdkVersionData(
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            null,
            null,
            null,
            null
        )

        private const val OPEN_TOKEN = """(?<open>[\(\[])?"""
        private const val CLOSE_TOKEN = """(?<close>[\)\]])?"""
        private const val SEPARATOR = """(?<separator>,)?"""
        private fun versionMatcher(i: Int) = """(?<version${i}>[^,\]\)]*)?"""
        private val VERSION_RANGE = Pattern.compile("^${OPEN_TOKEN}${versionMatcher(0)}${SEPARATOR}${versionMatcher(1)}${CLOSE_TOKEN}$")

        @Throws(InvalidVersionRange::class)
        fun parse(rangeMatcher: String): Predicate<OpenjdkVersionData> {

            try {
                if (!rangeMatcher.startsWith("[") && !rangeMatcher.startsWith("(")) {
                    val version = VersionParser.parse(rangeMatcher, sanityCheck = false, exactMatch = true)
                    return Predicate<OpenjdkVersionData> { version == it }
                }

                val match = VERSION_RANGE.matcher(rangeMatcher)

                if (match.matches()) {
                    val openPred = getOpenRestriction(match.group("open"))
                    val closePred = getCloseRestriction(match.group("close"))

                    val v0 = match.group("version0")
                    val v1 = match.group("version1")

                    if (
                        (v0 == null || v0.isEmpty()) &&
                        (v1 == null || v1.isEmpty())) {
                        throw InvalidVersionRange(rangeMatcher)
                    }

                    if ((openPred == EXACT || closePred == EXACT) && closePred != openPred) {
                        throw InvalidVersionRange(rangeMatcher)
                    }

                    val v0OpenjdkVersion = parseOpenjdkVersionData(v0, MIN_VERSION)

                    val v1OpenjdkVersion = if (match.group("separator") == null) {
                        v0OpenjdkVersion
                    } else {
                        parseOpenjdkVersionData(v1, MAX_VERSION)
                    }

                    return Predicate<OpenjdkVersionData> {
                        openPred.test(it, v0OpenjdkVersion) && closePred.test(it, v1OpenjdkVersion)
                    }
                }
            } catch (_: Exception) {
            }
            throw InvalidVersionRange(rangeMatcher)
        }

        private fun parseOpenjdkVersionData(version: String, default: OpenjdkVersionData) = if (version.isNotEmpty()) {
            VersionParser.parse(version, sanityCheck = false, exactMatch = true)
        } else {
            default
        }

        @Throws(RuntimeException::class)
        private fun getOpenRestriction(group: String?): OpenRestriction<OpenjdkVersionData> {
            return when (group) {
                null -> EXACT
                "[" -> GTE
                "(" -> GT
                else -> throw RuntimeException("Unknown token $group")
            }
        }

        @Throws(RuntimeException::class)
        private fun getCloseRestriction(group: String?): CloseRestriction<OpenjdkVersionData> {
            return when (group) {
                null -> EXACT
                "]" -> LTE
                ")" -> LT
                else -> throw RuntimeException("Unknown token $group")
            }
        }
    }

    class InvalidVersionRange(rangeMatcher: String) : Exception("Failed to form matcher for $rangeMatcher")
}
