package net.adoptium.api.v3.stats

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.stats.cloudflare.CloudflareStatsCalculator
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import org.slf4j.LoggerFactory

@ApplicationScoped
open class StatsInterface @Inject constructor(
    private val gitHubDownloadStatsCalculator: GitHubDownloadStatsCalculator,
    private val cloudFlareStatsCalculator: CloudflareStatsCalculator,
    dockerStatsInterfaceFactory: DockerStatsInterfaceFactory
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val dockerStats = dockerStatsInterfaceFactory.get()

    open suspend fun update(repos: AdoptRepos) {
        handlingUpdateException("github") { gitHubDownloadStatsCalculator.saveStats(repos) }
        handlingUpdateException("docker") { dockerStats.updateDb() }
        handlingUpdateException("cloudflare") { cloudFlareStatsCalculator.updateDb() }
    }

    private suspend fun handlingUpdateException(source: String, fn: suspend () -> Unit) = try {
        fn()
    } catch (e: Exception) {
        LOGGER.error("Failed to update $source, continuing with other sources", e)
    }

    open suspend fun updateStats() {}
}
