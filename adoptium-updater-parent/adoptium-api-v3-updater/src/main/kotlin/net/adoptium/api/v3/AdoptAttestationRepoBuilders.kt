package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@ApplicationScoped
class AdoptAttestationReposBuilder @Inject constructor(
    private var adoptAttestationRepository: AdoptAttestationRepository
    ) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun build(): AdoptAttestationRepos {
        val attestations = adoptAttestationRepository.getAttestations()
        LOGGER.info("DONE attestation build")
        return AdoptAttestationRepos(attestations)
    }

    suspend fun incrementalUpdate(
        oldRepo: AdoptAttestationRepos,
        lastUpdatedAt: UpdatedInfo
    ): AdoptAttestationRepos {
        val attestations = adoptAttestationRepository.incrementalUpdate(oldRepo, lastUpdatedAt)
        LOGGER.info("DONE attestation incrementalUpdate")
        return AdoptAttestationRepos(attestations)
    }
}
