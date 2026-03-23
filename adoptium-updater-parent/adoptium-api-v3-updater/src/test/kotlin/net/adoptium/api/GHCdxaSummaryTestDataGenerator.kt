package net.adoptium.api

import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryEntry
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryObject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryDefaultBranchRef
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryDefaultBranchRefTarget
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryDefaultBranchRefHistory
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryDefaultBranchRefNode
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

object GHCdxaSummaryTestDataGenerator {

    private val LOGGER = LoggerFactory.getLogger(this::class.java)

    fun generateGHCdxaRepoSummary(repo: AdoptCdxaRepos, directory: String): GHCdxaRepoSummaryData {
        val cdxas = repo.getCdxas()
        LOGGER.info("generateGHCdxaRepoSummary: "+cdxas+" directory: "+directory)

        var summaries = mutableListOf<GHCdxaRepoSummaryEntry>()

        // Is summary:
        //  root ""
        //  featureVersion "NN"
        //  releaseTag "NN/tag"
        if ( directory == "" ) {
          // Summary of feature versions..
          for( cdxa in cdxas ) {
            var versionEntry = summaries.firstOrNull { it.name == cdxa.featureVersion.toString() }
            if ( versionEntry == null ) {
                summaries.add(GHCdxaRepoSummaryEntry(cdxa.featureVersion.toString(), "tree"))
            }
          }
        } else if ( ! directory.contains("/") ) {
          // Summary of given feature version release tags..
          val featureVersion = directory.toIntOrNull()
          if ( featureVersion != null ) {
            for( cdxa in cdxas ) {
              if ( cdxa.featureVersion == featureVersion ) {
                var versionEntry = summaries.firstOrNull { it.name == cdxa.release_name }
                if ( versionEntry == null ) {
                  summaries.add(GHCdxaRepoSummaryEntry(cdxa.release_name?:"", "tree"))
                }
              }
            }
          }
        } else {
          // Summary of Cdxa within a given release tag..
          val subdirs = directory.split("/")
          val featureVersion = subdirs[0].toIntOrNull()
          val release_name   = subdirs[1]
          if ( featureVersion != null && release_name != null) {
            for( cdxa in cdxas ) {
              if ( cdxa.featureVersion == featureVersion && cdxa.release_name == release_name) {
                summaries.add(GHCdxaRepoSummaryEntry(cdxa.filename.substringAfterLast("/"), "blob"))
              }
            }
          }
        }

        val defaultBranchRef = GHCdxaRepoSummaryDefaultBranchRef(
                                 GHCdxaRepoSummaryDefaultBranchRefTarget(
                                   GHCdxaRepoSummaryDefaultBranchRefHistory(
                                     listOf(GHCdxaRepoSummaryDefaultBranchRefNode("2025-01-06T10:30:00Z")))))

        val cdxaSummary = GHCdxaRepoSummaryData(GHCdxaRepoSummaryRepository(defaultBranchRef, GHCdxaRepoSummaryObject(summaries)), RateLimit(1,1000))

        LOGGER.info("generateGHCdxaRepoSummary: "+cdxaSummary)

        return cdxaSummary
    }
}
