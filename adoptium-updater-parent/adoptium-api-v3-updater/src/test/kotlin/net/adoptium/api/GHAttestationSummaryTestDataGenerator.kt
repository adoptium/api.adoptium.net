package net.adoptium.api

import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryEntry
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryObject
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

object GHAttestationSummaryTestDataGenerator {

    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    fun generateGHAttestationRepoSummary(repo: AdoptAttestationRepos): GHAttestationRepoSummary {
        val attestations = repo.getAttestations()
        LOGGER.info("generateGHAttestationRepoSummary: "+attestations)

        var summaries = mutableListOf<GHAttestationRepoSummaryEntry>()
        for( attestation in attestations ) {
            var versionEntry = summaries.firstOrNull { it.name == attestation.featureVersion.toString() }
            if ( versionEntry == null ) {
                // No "tree" entry for this featureVersion yet, add List..
                summaries.add(
                        GHAttestationRepoSummaryEntry(attestation.featureVersion.toString(), "tree",
                                                      GHAttestationRepoSummaryObject(null,
                                                                                     listOf(GHAttestationRepoSummaryEntry(attestation.filename.substringAfter("/"),
                                                                                                                          "blob",
                                                                                                                          GHAttestationRepoSummaryObject(attestation.commitResourcePath, null)
                                                                                                                         )
                                                                                     )
                                                      )
                                                     ))
            } else {
                // Add to existing "tree" for featureVersion
                versionEntry.att_object?.entries = (versionEntry.att_object?.entries ?: mutableListOf<GHAttestationRepoSummaryEntry>()) + GHAttestationRepoSummaryEntry(attestation.filename.substringAfter("/"),
                                                                                                                    "blob",
                                                                                                                    GHAttestationRepoSummaryObject(attestation.commitResourcePath, null)
                                                                                                                   )
            }
        }

        val attestationSummary = GHAttestationRepoSummary(GHAttestationRepoSummaryData(GHAttestationRepoSummaryRepository(GHAttestationRepoSummaryObject(null, summaries))), RateLimit(1,1))

        return attestationSummary
    }
}
