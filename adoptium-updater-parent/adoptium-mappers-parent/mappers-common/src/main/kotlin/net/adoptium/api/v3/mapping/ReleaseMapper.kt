package net.adoptium.api.v3.mapping

import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.models.ReleaseType
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

abstract class ReleaseMapper {
    abstract suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult

    companion object {
        fun releaseType(name: String, url: String, isPrerelease: Boolean): ReleaseType {
            val ea = if (url.matches(Regex(".*/(temurin|openjdk|semeru)[0-9]+-binaries/.*"))) {
                isPrerelease
            } else {
                // Legacy repositories encode nightly status in the release name.
                Regex(".*(20[0-9]{2}-[0-9]{2}-[0-9]{2}|20[0-9]{6}).*").matches(name)
            }
            return if (ea) ReleaseType.ea else ReleaseType.ga
        }

        fun parseDate(date: String): ZonedDateTime {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date))
                .atZone(TimeSource.ZONE)
        }
    }
}
