package net.adoptium.api.v3.mapping

import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

abstract class CdxaMapper {
    abstract suspend fun toCdxaList(vendor: Vendor, ghCdxaAssets: List<GHCdxa>): List<Cdxa>
    abstract suspend fun toCdxa(vendor: Vendor, ghCdxa: GHCdxa): Cdxa?

    companion object {
        fun parseDate(date: String): ZonedDateTime {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date))
                .atZone(TimeSource.ZONE)
        }
    }
}
