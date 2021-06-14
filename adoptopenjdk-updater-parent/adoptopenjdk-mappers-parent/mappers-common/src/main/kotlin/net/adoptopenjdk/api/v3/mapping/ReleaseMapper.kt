package net.adoptopenjdk.api.v3.mapping

import net.adoptopenjdk.api.v3.ReleaseResult
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

abstract class ReleaseMapper {
    abstract suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult

    companion object {
        fun parseDate(date: String): ZonedDateTime {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date))
                .atZone(TimeSource.ZONE)
        }
    }
}
