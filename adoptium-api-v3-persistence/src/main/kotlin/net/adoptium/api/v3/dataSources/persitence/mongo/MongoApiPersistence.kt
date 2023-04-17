package net.adoptium.api.v3.dataSources.persitence.mongo

import com.mongodb.client.model.InsertManyOptions
import com.mongodb.client.model.UpdateOptions
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Model
import jakarta.inject.Inject
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.DockerDownloadStatsDbEntry
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.GitHubDownloadStatsDbEntry
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseInfo
import net.adoptium.api.v3.models.Vendor
import org.bson.BsonArray
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

@ApplicationScoped
open class MongoApiPersistence @Inject constructor(mongoClient: MongoClient) : MongoInterface(), ApiPersistence {
    private val githubReleaseMetadataCollection: CoroutineCollection<GHReleaseMetadata> = createCollection(mongoClient.database, GH_RELEASE_METADATA)
    private val releasesCollection: CoroutineCollection<Release> = createCollection(mongoClient.database, RELEASE_DB)
    private val gitHubStatsCollection: CoroutineCollection<GitHubDownloadStatsDbEntry> = createCollection(mongoClient.database, GITHUB_STATS_DB)
    private val dockerStatsCollection: CoroutineCollection<DockerDownloadStatsDbEntry> = createCollection(mongoClient.database, DOCKER_STATS_DB)
    private val releaseInfoCollection: CoroutineCollection<ReleaseInfo> = createCollection(mongoClient.database, RELEASE_INFO_DB)
    private val updateTimeCollection: CoroutineCollection<UpdatedInfo> = createCollection(mongoClient.database, UPDATE_TIME_DB)
    private val githubReleaseNotesCollection: CoroutineCollection<ReleaseNotes> = createCollection(mongoClient.database, GH_RELEASE_NOTES)

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        const val GH_RELEASE_METADATA = "githubReleaseMetadata"
        const val RELEASE_DB = "release"
        const val GITHUB_STATS_DB = "githubStats"
        const val DOCKER_STATS_DB = "dockerStats"
        const val RELEASE_INFO_DB = "releaseInfo"
        const val UPDATE_TIME_DB = "updateTime"
        const val GH_RELEASE_NOTES = "releaseNotes"
    }

    override suspend fun updateAllRepos(repos: AdoptRepos, checksum: String) {

        try {
            repos
                .repos
                .forEach { repo ->
                    writeReleases(repo.key, repo.value)
                }
        } finally {
            updateUpdatedTime(TimeSource.now(), checksum, repos.hashCode())
        }
    }

    private suspend fun writeReleases(featureVersion: Int, value: FeatureRelease) {
        val toAdd = value.releases.getReleases().toList()
        if (toAdd.isNotEmpty()) {
            releasesCollection.deleteMany(majorVersionMatcher(featureVersion))
            releasesCollection.insertMany(toAdd, InsertManyOptions())
        }
    }

    override suspend fun readReleaseData(featureVersion: Int): FeatureRelease {
        val releases = releasesCollection
            .find(majorVersionMatcher(featureVersion))
            .toList()

        return FeatureRelease(featureVersion, Releases(releases))
    }

    override suspend fun addGithubDownloadStatsEntries(stats: List<GitHubDownloadStatsDbEntry>) {
        gitHubStatsCollection.insertMany(stats)
    }

    override suspend fun getStatsForFeatureVersion(featureVersion: Int): List<GitHubDownloadStatsDbEntry> {
        return gitHubStatsCollection.find(Document("version.major", featureVersion))
            .toList()
    }

    override suspend fun getLatestGithubStatsForFeatureVersion(featureVersion: Int): GitHubDownloadStatsDbEntry? {
        return gitHubStatsCollection
            .find(Document("feature_version", featureVersion))
            .sort(Document("date", -1))
            .limit(1)
            .first()
    }

    override suspend fun getGithubStats(start: ZonedDateTime, end: ZonedDateTime): List<GitHubDownloadStatsDbEntry> {
        return gitHubStatsCollection
            .find(betweenDates(start, end))
            .sort(Document("date", 1))
            .toList()
    }

    override suspend fun getDockerStats(start: ZonedDateTime, end: ZonedDateTime): List<DockerDownloadStatsDbEntry> {
        return dockerStatsCollection
            .find(betweenDates(start, end))
            .sort(Document("date", 1))
            .toList()
    }

    override suspend fun addDockerDownloadStatsEntries(stats: List<DockerDownloadStatsDbEntry>) {
        dockerStatsCollection.insertMany(stats)
    }

    override suspend fun getLatestAllDockerStats(): List<DockerDownloadStatsDbEntry> {

        val repoNames = dockerStatsCollection.distinct<String>("repo").toList()

        return repoNames
            .mapNotNull {
                dockerStatsCollection
                    .find(Document("repo", it))
                    .sort(Document("date", -1))
                    .limit(1)
                    .first()
            }
            .toList()
    }

    override suspend fun removeStatsBetween(start: ZonedDateTime, end: ZonedDateTime) {
        val deleteQuery = betweenDates(start, end)
        dockerStatsCollection.deleteMany(deleteQuery)
        gitHubStatsCollection.deleteMany(deleteQuery)
    }

    override suspend fun setReleaseInfo(releaseInfo: ReleaseInfo) {
        releaseInfoCollection.deleteMany(releaseVersionDbEntryMatcher())
        releaseInfoCollection.updateOne(
            releaseVersionDbEntryMatcher(),
            releaseInfo,
            UpdateOptions().upsert(true)
        )
    }

    // visible for testing
    open suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
        updateTimeCollection.updateOne(
            Document(),
            UpdatedInfo(dateTime, checksum, hashCode),
            UpdateOptions().upsert(true)
        )
        updateTimeCollection.deleteMany(Document("time", BsonDocument("\$lt", BsonDateTime(dateTime.toInstant().toEpochMilli()))))
    }

    override suspend fun getUpdatedAt(): UpdatedInfo {
        val info = updateTimeCollection.findOne()
        // if we have no existing time, make it 5 mins ago, should only happen on first time the db is used
        return info ?: UpdatedInfo(TimeSource.now().minusMinutes(5), "000", 0)
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return releaseInfoCollection.findOne(releaseVersionDbEntryMatcher())
    }

    private fun releaseVersionDbEntryMatcher() = Document("tip_version", BsonDocument("\$exists", BsonBoolean(true)))

    private fun betweenDates(start: ZonedDateTime, end: ZonedDateTime): Document {
        return Document(
            "\$and",
            BsonArray(
                listOf(
                    BsonDocument("date", BsonDocument("\$gt", BsonDateTime(start.toInstant().toEpochMilli()))),
                    BsonDocument("date", BsonDocument("\$lt", BsonDateTime(end.toInstant().toEpochMilli())))
                )
            )
        )
    }

    private fun majorVersionMatcher(featureVersion: Int) = Document("version_data.major", featureVersion)

    override suspend fun getGhReleaseMetadata(gitHubId: GitHubId): GHReleaseMetadata? {
        return githubReleaseMetadataCollection.findOne(matchGithubId(gitHubId))
    }

    override suspend fun setGhReleaseMetadata(ghReleaseMetadata: GHReleaseMetadata) {
        githubReleaseMetadataCollection
            .updateOne(
                matchGithubId(ghReleaseMetadata.gitHubId),
                ghReleaseMetadata,
                UpdateOptions().upsert(true)
            )
    }

    override suspend fun hasReleaseNotesForGithubId(gitHubId: GitHubId): Boolean {
        return githubReleaseNotesCollection.findOne(Document("id", gitHubId.id)) != null
    }

    override suspend fun putReleaseNote(releaseNotes: ReleaseNotes) {
        githubReleaseNotesCollection.deleteMany(Document("id", releaseNotes.id))
        githubReleaseNotesCollection.insertOne(releaseNotes)
    }

    override suspend fun getReleaseNotes(vendor: Vendor, releaseName: String): ReleaseNotes? {
        return githubReleaseNotesCollection.findOne(Document(
            "\$and",
            BsonArray(
                listOf(
                    BsonDocument("release_name", BsonString(releaseName)),
                    BsonDocument("vendor", BsonString(vendor.name)),
                )
            )
        )
        )
    }

    private fun matchGithubId(gitHubId: GitHubId) = Document("gitHubId.id", gitHubId.id)
}
