package net.adoptium.api.v3.parser.maven

import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.FailedToParse
import java.util.regex.Pattern

object SemverParser {
    private const val PRE = """(\-(?<pre>[\.A-Za-z0-9]+))?"""
    private const val BUILD = """(\+(?<build>[\.A-Za-z0-9]+))?"""
    private const val VERSION_CORE = """(?<major>[0-9]+)\.(?<minor>[0-9]+)\.(?<patch>[0-9]+)$PRE$BUILD"""
    private val MATCHER = Pattern.compile("^$VERSION_CORE$")

    fun parseAdoptSemverNonNull(version: String): VersionData {
        return parseAdoptSemver(version) ?: throw FailedToParse("Failed to parse $version")
    }

    fun parseAdoptSemver(version: String): VersionData? {
        val matcher = MATCHER.matcher(version)

        if (matcher.matches()) {
            try {
                val major = matcher.group("major").toInt()
                val minor = matcher.group("minor").toInt()
                val security = matcher.group("patch").toInt()

                val semverPre = try {
                    matcher.group("pre")
                } catch (e: IllegalArgumentException) {
                    null
                }

                val semverBuild = try {
                    matcher.group("build")
                } catch (e: IllegalArgumentException) {
                    null
                }

                var adoptBuildNum: Int? = null
                var patch: Int? = null
                var build: Int? = null
                var optional: String? = null

                try {
                    if (semverBuild != null) {
                        val parts = semverBuild
                            .split(""".""")
                            .toList()


                        optional = if (parts.size > 2) parts[2] else null
                        adoptBuildNum = if (parts.size > 1) parts[1].toInt() else null
                        build = if (parts.isNotEmpty()) parts[0].toInt() else null

                        if (build != null) {
                            patch = build / 100
                            build -= (patch * 100)
                        }
                    }
                } catch (e: NumberFormatException) {
                    //ignore non-int
                }

                if (patch == 0) patch = null

                var parts = if (patch != null) {
                    listOf(major, minor, security, patch)
                } else {
                    listOf(major, minor, security)
                }

                while (parts.size > 1 && parts.last() == 0) {
                    parts = parts.dropLast(1)
                }

                var ojdkVersion = parts.joinToString(".")
                ojdkVersion = if (semverPre != null) "$ojdkVersion-$semverPre" else ojdkVersion

                ojdkVersion = if (build != null || optional != null) "$ojdkVersion+" else ojdkVersion
                ojdkVersion = if (build != null) "$ojdkVersion$build" else ojdkVersion
                ojdkVersion = if (optional != null) "$ojdkVersion-$optional" else ojdkVersion


                return VersionData(
                    major,
                    minor,
                    security,
                    semverPre,
                    adoptBuildNum,
                    build ?: 0,
                    optional,
                    ojdkVersion,
                    version,
                    patch
                )
            } catch (_: IllegalArgumentException) {
            } catch (_: NumberFormatException) {
            }

        }
        return null
    }
}
