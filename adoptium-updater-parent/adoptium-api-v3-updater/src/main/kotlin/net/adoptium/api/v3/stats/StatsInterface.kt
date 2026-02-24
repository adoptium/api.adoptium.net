package net.adoptium.api.v3.stats

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.stats.cloudflare.CloudflareStatsCalculator
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory

@ApplicationScoped
open class StatsInterface @Inject constructor(
    private val gitHubDownloadStatsCalculator: GitHubDownloadStatsCalculator,
    private val cloudFlareStatsCalculator: CloudflareStatsCalculator,
    dockerStatsInterfaceFactory: DockerStatsInterfaceFactory
) {
    private val dockerStats = dockerStatsInterfaceFactory.get()
    open suspend fun update(repos: AdoptRepos) {
        gitHubDownloadStatsCalculator.saveStats(repos)
        dockerStats.updateDb()
        cloudFlareStatsCalculator.updateDb()
    }

    open suspend fun updateStats() {}
}
