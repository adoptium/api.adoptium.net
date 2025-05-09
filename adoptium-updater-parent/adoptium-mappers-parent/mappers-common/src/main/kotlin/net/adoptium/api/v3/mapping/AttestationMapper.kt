package net.adoptium.api.v3.mapping

import net.adoptium.api.v3.models.Attestation
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestation
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

abstract class AttestationMapper {
    abstract suspend fun toAttestationList(ghAttestationAssets: List<GHAttestation>): List<Attestation>

    companion object {
        fun parseDate(date: String): ZonedDateTime {
            return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date))
                .atZone(TimeSource.ZONE)
        }
    }
}
