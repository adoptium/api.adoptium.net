package net.adoptium.api

import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryEntry
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryObject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryDefaultBranchRef
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryDefaultBranchRefTarget
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryDefaultBranchRefHistory
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryDefaultBranchRefNode
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

object GHAttestationSummaryTestDataGenerator {

    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    fun generateGHAttestationRepoSummary(repo: AdoptAttestationRepos, directory: String): GHAttestationRepoSummaryData {
        val attestations = repo.getAttestations()
        LOGGER.info("generateGHAttestationRepoSummary: "+attestations+" directory: "+directory)

        var summaries = mutableListOf<GHAttestationRepoSummaryEntry>()

        // Is summary:
        //  root ""
        //  featureVersion "NN"
        //  releaseTag "NN/tag"
        if ( directory == "" ) {
          // Summary of feature versions..
          for( attestation in attestations ) {
            var versionEntry = summaries.firstOrNull { it.name == attestation.featureVersion.toString() }
            if ( versionEntry == null ) {
                summaries.add(GHAttestationRepoSummaryEntry(attestation.featureVersion.toString(), "tree"))
            }
          }
        } else if ( ! directory.contains("/") ) {
          // Summary of given feature version release tags..
          val featureVersion = directory.toIntOrNull()
          if ( featureVersion != null ) {
            for( attestation in attestations ) {
              if ( attestation.featureVersion == featureVersion ) {
                var versionEntry = summaries.firstOrNull { it.name == attestation.release_name }
                if ( versionEntry == null ) {
                  summaries.add(GHAttestationRepoSummaryEntry(attestation.release_name?:"", "tree"))
                }
              }
            }
          }
        } else {
          // Summary of Attestation within a given release tag..
          val subdirs = directory.split("/")
          val featureVersion = subdirs[0].toIntOrNull()
          val release_name   = subdirs[1]
          if ( featureVersion != null && release_name != null) {
            for( attestation in attestations ) {
              if ( attestation.featureVersion == featureVersion && attestation.release_name == release_name) {
                summaries.add(GHAttestationRepoSummaryEntry(attestation.filename.substringAfterLast("/"), "blob"))
              }
            }
          }
        }

        val defaultBranchRef = GHAttestationRepoSummaryDefaultBranchRef(
                                 GHAttestationRepoSummaryDefaultBranchRefTarget(
                                   GHAttestationRepoSummaryDefaultBranchRefHistory(
                                     listOf(GHAttestationRepoSummaryDefaultBranchRefNode("2025-01-06T10:30:00Z")))))

        val attestationSummary = GHAttestationRepoSummaryData(GHAttestationRepoSummaryRepository(defaultBranchRef, GHAttestationRepoSummaryObject(summaries)), RateLimit(1,1000))

        LOGGER.info("generateGHAttestationRepoSummary: "+attestationSummary)

        return attestationSummary
    }
}
