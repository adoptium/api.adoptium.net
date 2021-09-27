package net.adoptopenjdk.api.v3.stats

import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.stats.dockerstats.DockerStatsInterfaceFactory
import javax.inject.Inject

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
