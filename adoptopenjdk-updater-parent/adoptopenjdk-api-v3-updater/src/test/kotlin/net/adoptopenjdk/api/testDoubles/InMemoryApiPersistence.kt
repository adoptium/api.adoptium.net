package net.adoptopenjdk.api.testDoubles

import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.persitence.ApiPersistence
import net.adoptopenjdk.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptopenjdk.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptopenjdk.api.v3.models.ReleaseInfo
import java.time.ZonedDateTime
import javax.annotation.Priority
import javax.enterprise.inject.Alternative
import javax.inject.Inject
import javax.inject.Singleton

@Priority(1)
@Alternative
@Singleton
open class InMemoryApiPersistence @Inject constructor(var repos: AdoptRepos) : ApiPersistence {
    private var updatedAtInfo: UpdatedInfo? = null
    private var releaseInfo: ReleaseInfo? = null

    private var githubStats = ArrayList<GitHubDownloadStatsDbEntry>()
    private var dockerStats = ArrayList<DockerDownloadStatsDbEntry>()

    override suspend fun updateAllRepos(repos: AdoptRepos, checksum: String) {
        this.repos = repos
        this.updatedAtInfo = UpdatedInfo(TimeSource.now(), checksum, repos.hashCode())
    }

    override suspend fun readReleaseData(featureVersion: Int): FeatureRelease {
        return repos!!.getFeatureRelease(featureVersion) ?: FeatureRelease(featureVersion, emptyList())
    }

    override suspend fun addGithubDownloadStatsEntries(stats: List<GitHubDownloadStatsDbEntry>) {
        githubStats.addAll(stats)
    }

    override suspend fun getStatsForFeatureVersion(featureVersion: Int): List<GitHubDownloadStatsDbEntry> {
        return githubStats.filter { stats -> stats.feature_version == featureVersion }
            .sortedBy { it.date }
    }

    override suspend fun getLatestGithubStatsForFeatureVersion(featureVersion: Int): GitHubDownloadStatsDbEntry? {
        return getStatsForFeatureVersion(featureVersion).lastOrNull()
    }

    override suspend fun getGithubStats(start: ZonedDateTime, end: ZonedDateTime): List<GitHubDownloadStatsDbEntry> {
        return githubStats.filter { stats -> stats.date.isAfter(start) && stats.date.isBefore(end) }
            .sortedBy { it.date }
    }

    override suspend fun getDockerStats(start: ZonedDateTime, end: ZonedDateTime): List<DockerDownloadStatsDbEntry> {
        return dockerStats.filter { stats -> stats.date.isAfter(start) && stats.date.isBefore(end) }
            .sortedBy { it.date }
    }

    override suspend fun addDockerDownloadStatsEntries(stats: List<DockerDownloadStatsDbEntry>) {
        dockerStats.addAll(stats)
    }

    override suspend fun getLatestAllDockerStats(): List<DockerDownloadStatsDbEntry> {
        return dockerStats
            .map { it.repo }
            .distinct()
            .map { name ->
                dockerStats
                    .filter { name == it.repo }
                    .sortedBy { it.date }
                    .last()
            }
    }

    override suspend fun removeStatsBetween(start: ZonedDateTime, end: ZonedDateTime) {
        TODO("Not yet implemented")
    }

    override suspend fun setReleaseInfo(releaseInfo: ReleaseInfo) {
        this.releaseInfo = releaseInfo
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return releaseInfo
    }

    override suspend fun getUpdatedAt(): UpdatedInfo {
        return updatedAtInfo ?: UpdatedInfo(TimeSource.now().minusMinutes(5), "000", 0)
    }
}
