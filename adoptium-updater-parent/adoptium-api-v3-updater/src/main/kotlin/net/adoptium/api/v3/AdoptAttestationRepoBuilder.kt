package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepo
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@ApplicationScoped
class AdoptAttestationRepoBuilder @Inject constructor(
    private var adoptAttestationRepository: AdoptAttestationRepository,
    private var versionSupplier: VersionSupplier
    ) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun build(): AdoptAttestationRepo {
        // Fetch attestations in parallel
        val attestationMap = versionSupplier
            .getAllVersions()
            .reversed()
            .mapNotNull { version ->
                adoptAttestationRepository.getAttestations(version)
            }
            .associateBy { it.featureVersion }
        LOGGER.info("DONE")
        return AdoptAttestationRepo(attestationMap)
    }
}
