package net.adoptopenjdk.api.v3.stats

import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import javax.inject.Inject

class StatsInterface @Inject constructor(
    private val gitHubDownloadStatsCalculator: GitHubDownloadStatsCalculator,
    private val dockerStatsInterface: DockerStatsInterface
) {
    suspend fun update(repos: AdoptRepos) {
        gitHubDownloadStatsCalculator.saveStats(repos)
        dockerStatsInterface.updateDb()
    }
}
