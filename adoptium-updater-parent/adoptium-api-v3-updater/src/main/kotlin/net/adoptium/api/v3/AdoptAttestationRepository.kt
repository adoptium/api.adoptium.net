package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestation
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryEntry
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.AttestationMapper
import net.adoptium.api.v3.mapping.adopt.AdoptAttestationMapperFactory
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import net.adoptium.api.v3.models.AttestationRepoVersionSummary
import net.adoptium.api.v3.config.APIConfig
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import org.slf4j.LoggerFactory
import java.time.Instant

interface AdoptAttestationRepository {
    suspend fun getAttestations(): List<Attestation>
    suspend fun getAttestationByName(vendor: Vendor, owner: String, repoName: String, name: String) : Attestation?
    suspend fun incrementalUpdate(oldRepo: AdoptAttestationRepos, lastUpdatedAt: UpdatedInfo): List<Attestation>
}

@ApplicationScoped
open class AdoptAttestationRepositoryImpl @Inject constructor(
    val client: GitHubApi,
    adoptAttestationMapperFactory: AdoptAttestationMapperFactory
) : AdoptAttestationRepository {

    companion object {
        const val ADOPTIUM_ORG = "adoptium"

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val mappers = mapOf(
        ".*/temurin-attestations/.*".toRegex() to adoptAttestationMapperFactory.get(Vendor.eclipse),
    )

    private fun getMapperForRepo(url: String): AttestationMapper {
        val mapper = mappers
            .entries
            .firstOrNull { url.matches(it.key) }

        if (mapper == null) {
            throw IllegalStateException("No mapper found for repo $url")
        }

        return mapper.value
    }

    override suspend fun getAttestationByName(vendor: Vendor, owner: String, repoName: String, name: String): Attestation? {
        val attestation = client.getAttestationByName(owner, repoName, name)

        if ( attestation != null ) {
            val attestation_link = owner + "/" + repoName + "/" + name
            LOGGER.info("Retrieved Attestation for: " + attestation_link)
            return getMapperForRepo(attestation_link).toAttestation(vendor, attestation)
        } else {
            return null
        }
    }

    private fun getRepository(): suspend (Vendor, String, String) -> List<Attestation> {
        return { vendor: Vendor, owner: String, repoName: String -> getRepository(vendor, owner, repoName) }
    }

    private suspend fun getRepository(vendor: Vendor, owner: String, repoName: String): List<Attestation> {
        val attestations = mutableListOf<Attestation>()

        LOGGER.info("Attestation getRepository for: " + vendor + " " + owner + "/" + repoName)

        // Repository structure:
        //   <major-version>/<release-tag>/<Attestations.xml|xml.sign.pub>

        // Get the top major version list
        var majorVersions = mutableListOf<Int>()
        val attSummaryTop = client.getAttestationSummary(owner, repoName, "")
        if ( attSummaryTop != null ) {
          // Determine major versions
          val topDirs = attSummaryTop?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?
          if ( topDirs != null) {
            for( dir in topDirs ) {
              if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                majorVersions.add(dir.name.toInt())
              }
            }
          }
        }

        // Get the Attestations for each major version
        for( majorVersion in majorVersions ) {
          attestations.addAll( getAttestationsForVersion( vendor, owner, repoName, majorVersion, null ) )
        }

        return attestations
    }

    private suspend fun getAttestationsForVersion(vendor: Vendor, owner: String, repoName: String, version: Int, afterDate: Instant?): List<Attestation> {
        LOGGER.info("getAttestationsForVersion: "+vendor+" "+owner+" "+repoName+" "+version+" modifiedAfterDate: "+afterDate)
        val attestations = mutableListOf<GHAttestation>()

        val attSummaryTags = client.getAttestationSummary(owner, repoName, "$version")
        if ( attSummaryTags != null ) {
          val tags = attSummaryTags?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?
          if ( tags != null) {
            for( tag in tags ) {
              if ( tag.type == "tree" ) {
                val releaseTag = tag.name

                // Get Attestations for this release tag
                val attEntries = client.getAttestationSummary(owner, repoName, "$version/"+releaseTag)
                if ( attEntries?.repository?.att_object?.entries != null) {
                  // Get last commit update date for this releaseTag
                  var committedDate = instantFromCommittedDate( owner, repoName, attEntries?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate )

                  // Have attestations for this release being updated?
                  if ( afterDate == null || committedDate == null || committedDate.isAfter(afterDate) ) {
                      for( attXml in attEntries?.repository?.att_object?.entries?: emptyList<GHAttestationRepoSummaryEntry>() ) {
                        // Attestation documents are .xml blob files
                        if ( attXml.type == "blob" && attXml.name.endsWith(".xml") ) {
                          val attestation = client.getAttestationByName(owner, repoName, "$version/" + releaseTag + "/" + attXml.name)
                          if ( attestation != null ) {
                            val attestation_link = owner + "/" + repoName + "/$version/" + releaseTag + "/" + attXml.name
                            LOGGER.info("Retrieved Attestation for: " + attestation_link)
                            attestations.add(attestation)
                          }
                        }
                      }
                  }
                }
              }
            }
          }
        }

        return getMapperForRepo(owner + "/" + repoName + "/").toAttestationList(vendor, attestations)
    }

    private fun getSummaries(): suspend (Vendor, String, String) -> List<AttestationRepoVersionSummary> {
        return { vendor: Vendor, owner: String, repoName: String -> getSummaries(vendor, owner, repoName) }
    }

    private suspend fun getSummaries(vendor: Vendor, owner: String, repoName: String): List<AttestationRepoVersionSummary> {
        val summaries = mutableListOf<AttestationRepoVersionSummary>()

        LOGGER.info("Attestations getSummaries for: " + vendor + " " + owner + "/" + repoName)

        // Repository structure:
        //   <major-version>/<release-tag>/<Attestations.xml|xml.sign.pub>

        // Get the top major versions committedDate summary
        val attSummaryTop = client.getAttestationSummary(owner, repoName, "")
        if ( attSummaryTop != null ) {
          // Determine major versions
          val topDirs = attSummaryTop?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?
          if ( topDirs != null) {
            for( dir in topDirs ) {
              if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                // Get summary of featureVersion so as to get its committedDate
                val attSummaryTags = client.getAttestationSummary(owner, repoName, dir.name)
                val committedDate  = instantFromCommittedDate( owner, repoName, attSummaryTags?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate )
                val releaseTags    = attSummaryTags?.repository?.att_object?.entries?.filter { it.type == "tree" }?.map { it.name } ?: emptyList<String>()

                summaries.add( AttestationRepoVersionSummary(vendor, owner, repoName, dir.name.toInt(), releaseTags, committedDate) )
              }
            }
          }
        }

        return summaries
    }

    private fun instantFromCommittedDate(owner: String, repoName: String, committedDate: String?): Instant? {
        var committedInstant: Instant? = try {
            if ( committedDate != null ) {
                Instant.parse( committedDate )
            } else {
                null
            }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Cannot parse attestation $owner/$repoName committedDate string: "+committedDate)
            null
        }

        return committedInstant
    }

    override suspend fun incrementalUpdate(
        oldRepo: AdoptAttestationRepos,
        lastUpdatedAt: UpdatedInfo
    ): List<Attestation> {

        val summaries: List<AttestationRepoVersionSummary> = getDataForEachRepo(
            getSummaries()
        ).await().flatMap { it.toList() }

        var updatedAttestations = oldRepo.repos.toMutableList()
        for( summary in summaries ) {
            if ( summary.committedDate == null || (summary.committedDate as Instant).isAfter( lastUpdatedAt.time.toInstant() ) ) {
                LOGGER.info("incrementalUpdate: version has been updated: "+summary)

                // Get Attestations for version releaseTags that have been updated after lastUpdatedAt
                val attestations = getAttestationsForVersion( summary.vendor, summary.org, summary.repo, summary.featureVersion, lastUpdatedAt.time.toInstant() )

                val updatedReleases = attestations.map { it.release_name }.distinct()

                // Remove updated version releases from old repo and replace with updated
                updatedAttestations.removeAll { it.featureVersion == summary.featureVersion && it.release_name in updatedReleases }
                updatedAttestations.addAll( attestations )

                // Remove any version releases for which there are no longer any attestations
                updatedAttestations.removeAll { it.featureVersion == summary.featureVersion && it.release_name !in summary.releaseTags }
            }
        }

        // Remove any major versions that have been removed all together..
        val currentVersions = summaries.map { it.featureVersion }.distinct()
        updatedAttestations.removeAll { it.featureVersion !in currentVersions }

        return updatedAttestations
    }

    override suspend fun getAttestations(): List<Attestation> {
        
        val attestations: List<Attestation> = getDataForEachRepo(
            getRepository()
        ).await().flatMap { it.toList() }
                
        return attestations
    }

    private fun <E> getDataForEachRepo(
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<List<E>> {
        return GlobalScope.async {

            return@async listOf(
                getRepoDataAsync(ADOPTIUM_ORG, Vendor.eclipse, "temurin-attestations", getFun),
            )
                .map { repo -> repo.await() }
        }
    }

    private fun <E> getRepoDataAsync(
        owner: String,
        vendor: Vendor,
        repoName: String,
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<E> {
        return GlobalScope.async {
            LOGGER.info("getting attestations for $owner $repoName")
            val attestations = getFun(vendor, owner, repoName)
            LOGGER.info("Done getting attestations for $owner $repoName")
            return@async attestations
        }
    }
}
