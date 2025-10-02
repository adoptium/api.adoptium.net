package net.adoptium.api.testDoubles

import jakarta.enterprise.context.ApplicationScoped
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import java.time.ZonedDateTime
import jakarta.annotation.Priority
import jakarta.enterprise.inject.Alternative
import jakarta.inject.Inject

@Priority(1)
@Alternative
@ApplicationScoped
open class InMemoryApiPersistence @Inject constructor(var repos: AdoptRepos, var attestationRepos: AdoptAttestationRepos) : ApiPersistence {
    private var updatedAtInfo: UpdatedInfo? = null
    private var attestationUpdatedAtInfo: UpdatedInfo? = null
    private var releaseInfo: ReleaseInfo? = null

    private var githubStats = ArrayList<GitHubDownloadStatsDbEntry>()
    private var dockerStats = ArrayList<DockerDownloadStatsDbEntry>()
    private var ghReleaseMetadata = HashMap<GitHubId, GHReleaseMetadata>()
    val releaseNotes = ArrayList<ReleaseNotes>()

    override suspend fun updateAllRepos(repos: AdoptRepos, checksum: String) {
        this.repos = repos
        this.updatedAtInfo = UpdatedInfo(TimeSource.now(), checksum, repos.hashCode())
    }

    override suspend fun updateAttestationRepos(repos: AdoptAttestationRepos, checksum: String) {
        this.attestationRepos = attestationRepos
        this.attestationUpdatedAtInfo = UpdatedInfo(TimeSource.now(), checksum, repos.hashCode())
    }

    override suspend fun readAttestationData(): List<Attestation> {
        return attestationRepos.getAttestations()
    }

    override suspend fun readReleaseData(featureVersion: Int): FeatureRelease {
        return repos.getFeatureRelease(featureVersion) ?: FeatureRelease(featureVersion, emptyList())
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

    override suspend fun getAttestationUpdatedAt(): UpdatedInfo {
        return attestationUpdatedAtInfo ?: UpdatedInfo(TimeSource.now().minusMinutes(5), "000", 0)
    }

    override suspend fun getGhReleaseMetadata(gitHubId: GitHubId): GHReleaseMetadata? {
        return ghReleaseMetadata[gitHubId]
    }

    override suspend fun setGhReleaseMetadata(ghReleaseMetadata: GHReleaseMetadata) {
        this.ghReleaseMetadata[ghReleaseMetadata.gitHubId] = ghReleaseMetadata
    }

    override suspend fun hasReleaseNotesForGithubId(gitHubId: GitHubId): Boolean {
        return releaseNotes.count { it.id == gitHubId.id } > 0
    }

    override suspend fun putReleaseNote(releaseNotes: ReleaseNotes) {
        this.releaseNotes.removeAll { it.id == releaseNotes.id }
        this.releaseNotes.add(releaseNotes)
    }

    override suspend fun getReleaseNotes(vendor: Vendor, releaseName: String): ReleaseNotes? {
        return releaseNotes
            .filter { it.vendor == vendor }
            .firstOrNull { it.release_name == releaseName }
    }

    override suspend fun isConnected(): Boolean {
        return true
    }
}
