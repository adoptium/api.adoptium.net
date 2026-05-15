package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
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
class AdoptCdxaReposBuilder @Inject constructor(
    private var adoptCdxaRepository: AdoptCdxaRepository
    ) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun build(): AdoptCdxaRepos {
        val (cdxas, lastModified) = adoptCdxaRepository.getCdxas()
        LOGGER.info("DONE cdxa build, lastModified: $lastModified")
        return AdoptCdxaRepos(cdxas, lastModified)
    }

    suspend fun incrementalUpdate(
        oldRepo: AdoptCdxaRepos,
        lastUpdatedAt: UpdatedInfo
    ): AdoptCdxaRepos {
        val (cdxas, lastModified) = adoptCdxaRepository.incrementalUpdate(oldRepo, lastUpdatedAt)
        LOGGER.info("DONE cdxa incrementalUpdate, lastModified: $lastModified")
        return AdoptCdxaRepos(cdxas, lastModified)
    }
}
