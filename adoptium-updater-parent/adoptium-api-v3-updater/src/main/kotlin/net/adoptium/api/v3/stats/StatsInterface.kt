package net.adoptium.api.v3.stats

import jakarta.enterprise.context.ApplicationScoped
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import jakarta.inject.Inject

@ApplicationScoped
class StatsInterface @Inject constructor(
    private val gitHubDownloadStatsCalculator: GitHubDownloadStatsCalculator,
    dockerStatsInterfaceFactory: DockerStatsInterfaceFactory
) {
    private val dockerStats = dockerStatsInterfaceFactory.get()
    suspend fun update(repos: AdoptRepos) {
        gitHubDownloadStatsCalculator.saveStats(repos)
        dockerStats.updateDb()
    }
}
