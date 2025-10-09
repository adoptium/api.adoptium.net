package net.adoptium.api.v3.dataSources.persitence

import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.AdoptAttestationRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.persitence.mongo.UpdatedInfo
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import java.time.ZonedDateTime

interface ApiPersistence {
    suspend fun updateAllRepos(repos: AdoptRepos, checksum: String)
    suspend fun updateAttestationRepos(repos: AdoptAttestationRepos, checksum: String)
    suspend fun readReleaseData(featureVersion: Int): FeatureRelease
    suspend fun readAttestationData(): List<Attestation>

    suspend fun addGithubDownloadStatsEntries(stats: List<GitHubDownloadStatsDbEntry>)
    suspend fun getStatsForFeatureVersion(featureVersion: Int): List<GitHubDownloadStatsDbEntry>
    suspend fun getLatestGithubStatsForFeatureVersion(featureVersion: Int): GitHubDownloadStatsDbEntry?
    suspend fun getGithubStats(start: ZonedDateTime, end: ZonedDateTime): List<GitHubDownloadStatsDbEntry>
    suspend fun getDockerStats(start: ZonedDateTime, end: ZonedDateTime): List<DockerDownloadStatsDbEntry>
    suspend fun addDockerDownloadStatsEntries(stats: List<DockerDownloadStatsDbEntry>)
    suspend fun getLatestAllDockerStats(): List<DockerDownloadStatsDbEntry>
    suspend fun removeStatsBetween(start: ZonedDateTime, end: ZonedDateTime)
    suspend fun setReleaseInfo(releaseInfo: ReleaseInfo)
    suspend fun getReleaseInfo(): ReleaseInfo?
    suspend fun getUpdatedAt(): UpdatedInfo
    suspend fun getAttestationUpdatedAt(): UpdatedInfo
    suspend fun getGhReleaseMetadata(gitHubId: GitHubId): GHReleaseMetadata?
    suspend fun setGhReleaseMetadata(ghReleaseMetadata: GHReleaseMetadata)
    suspend fun hasReleaseNotesForGithubId(gitHubId: GitHubId): Boolean
    suspend fun putReleaseNote(releaseNotes: ReleaseNotes)
    suspend fun getReleaseNotes(vendor: Vendor, releaseName: String): ReleaseNotes?
    suspend fun isConnected(): Boolean
}
