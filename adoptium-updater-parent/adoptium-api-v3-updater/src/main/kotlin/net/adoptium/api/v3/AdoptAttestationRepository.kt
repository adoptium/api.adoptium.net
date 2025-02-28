package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.AttestationMapper
import net.adoptium.api.v3.mapping.adopt.AdoptAttestationMapperFactory
import net.adoptium.api.v3.models.Vendor
import org.slf4j.LoggerFactory

interface AdoptAttestationRepository {
    suspend fun getAttestationsSummary(): AttestationRepoSummary?
    suspend fun getAttestationByName()  : GHAttestation?
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

    private fun getMapperForRepo(url: String): ReleaseMapper {
        val mapper = mappers
            .entries
            .firstOrNull { url.matches(it.key) }

        if (mapper == null) {
            throw IllegalStateException("No mapper found for repo $url")
        }

        return mapper.value
    }

    override suspend fun getAttestationByName(owner: String, repoName: String, name: String): GHAttestation? {
        val attestation = client.getAttestationByName(owner, repoName, name)

        return attestation
    }

    private fun getAttestationRepoSummary(): suspend (Vendor, String, String) -> AttestationRepoSummary? {
        return { vendor: Vendor, owner: String, repoName: String -> getAttestationRepoSummary(vendor, owner, repoName) }
    }

    private suspend fun getAttestationRepoSummary(vendor: Vendor, owner: String, repoName: String): AttestationRepoSummary? {
        return client.getAttestationSummary(owner, repoName)
    }

    private fun <E> getDataForEachRepo(
        version: Int,
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<List<E?>> {
        LOGGER.info("getting $version")
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
    ): Deferred<E?> {
        return GlobalScope.async {
            if (!Vendor.validVendor(vendor)) {
                return@async null
            }
            LOGGER.info("getting attestations for $owner $repoName")
            val attestations = getFun(vendor, owner, repoName)
            LOGGER.info("Done getting attestations for $owner $repoName")
            return@async attestations
        }
    }
}
