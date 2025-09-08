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
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.AttestationMapper
import net.adoptium.api.v3.mapping.adopt.AdoptAttestationMapperFactory
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
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
            return getMapperForRepo(owner + "/" + repoName).toAttestation(vendor, attestation)
        } else {
            return null
        }
    }

    private fun getRepository(): suspend (Vendor, String, String) -> List<Attestation> {
        return { vendor: Vendor, owner: String, repoName: String -> getRepository(vendor, owner, repoName) }
    }

    private suspend fun getRepository(vendor: Vendor, owner: String, repoName: String): List<Attestation> {
        var attestations = mutableListOf<GHAttestation>()

        val attSummary = client.getAttestationSummary(owner, repoName)

        if ( attSummary != null ) {
            val attSummaryEntries = attSummary?.data?.repository?.att_object?.entries //List<GHAttestationRepoSummaryEntry>?

            if ( attSummaryEntries != null) {
              for( dir in attSummaryEntries ) {
                // Attestations are within jdk "version" directories
                if ( dir.type == "tree" && dir.name.toIntOrNull() != null) {
                    val attVersionEntries = dir?.att_object?.entries
                    if ( attVersionEntries != null ) {
                      for( attXml in attVersionEntries ) {
                        // Attestation documents are .xml blob files
                        if ( attXml.type == "blob" && attXml.name.endsWith(".xml") ) {
                            val attestation = client.getAttestationByName(owner, repoName, dir.name + "/" + attXml.name)
                            if ( attestation != null ) {
                                attestations.add(attestation)
                            }
                        }
                      }
                    }
                }
              }
            }
        }

        return getMapperForRepo(owner + "/" + repoName).toAttestationList(vendor, attestations)
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
