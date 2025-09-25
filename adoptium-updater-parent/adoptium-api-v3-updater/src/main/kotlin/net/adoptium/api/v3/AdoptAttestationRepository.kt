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
import net.adoptium.api.v3.config.APIConfig
import org.slf4j.LoggerFactory

interface AdoptAttestationRepository {
    suspend fun getAttestations(): List<Attestation>
    suspend fun getAttestationByName(vendor: Vendor, owner: String, repoName: String, name: String) : Attestation?
}

@ApplicationScoped
open class AdoptAttestationRepositoryImpl @Inject constructor(
    val client: GitHubApi,
    adoptAttestationMapperFactory: AdoptAttestationMapperFactory
) : AdoptAttestationRepository {

    companion object {
        const val ADOPT_ORG = "AdoptOpenJDK"
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
        val attestations = mutableListOf<GHAttestation>()

        LOGGER.info("Attestation getRepository for: " + vendor + " " + owner + "/" + repoName)

        // Repository structure:
        //   <major-version>/<release-tag>/<Attestations.xml|xml.sign.pub>

        // Get the top major version list
        var majorVersions = mutableListOf<String>()
        val attSummaryTop = client.getAttestationSummary(owner, repoName, "")
        if ( attSummaryTop != null ) {
          // Determine major versions
          val topDirs = attSummaryTop?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?
          if ( topDirs != null) {
            for( dir in topDirs ) {
              if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                majorVersions.add(dir.name)
              }
            }
          }
        }

        // Get the release tags for each major version
        for( majorVersion in majorVersions ) {
          val attSummaryTags = client.getAttestationSummary(owner, repoName, majorVersion)
          if ( attSummaryTags != null ) {
            val tags = attSummaryTags?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?
            if ( tags != null) {
              for( tag in tags ) {
                if ( tag.type == "tree" ) {
                  val releaseTag = tag.name

                  // Get Attestations for this release tag
                  val attEntries = client.getAttestationSummary(owner, repoName, majorVersion+"/"+releaseTag)
                  if ( attEntries?.repository?.att_object?.entries != null) {
                    // Get last commit update date for this releaseTag
                    val committedDate = attEntries?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate

                    for( attXml in attEntries?.repository?.att_object?.entries?: emptyList<GHAttestationRepoSummaryEntry>() ) {
                      // Attestation documents are .xml blob files
                      if ( attXml.type == "blob" && attXml.name.endsWith(".xml") ) {
                        val attestation = client.getAttestationByName(owner, repoName, majorVersion + "/" + releaseTag + "/" + attXml.name)
                        if ( attestation != null ) {
                          val attestation_link = owner + "/" + repoName + "/" + majorVersion + "/" + releaseTag + "/" + attXml.name
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
