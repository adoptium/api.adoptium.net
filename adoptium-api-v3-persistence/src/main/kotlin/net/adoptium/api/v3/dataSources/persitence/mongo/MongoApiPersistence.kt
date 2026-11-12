package net.adoptium.api.v3.dataSources.persitence.mongo

import com.mongodb.MongoCommandException
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.InsertManyOptions
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptCdxaRepos
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.ReleaseNotes
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.dataSources.persitence.ApiPersistence
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.CloudflarePackageDownloadStatsDbEntry
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
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

@ApplicationScoped
open class MongoApiPersistence @Inject constructor(mongoClient: MongoClient) : MongoInterface(), ApiPersistence {
    private val githubReleaseMetadataCollection: MongoCollection<GHReleaseMetadata> = createCollection(mongoClient.getDatabase(), GH_RELEASE_METADATA)
    private val releasesCollection: MongoCollection<Release> = createCollection(
        mongoClient.getDatabase(),
        RELEASE_DB,
        ensureIndexes = { collection ->
            try {
                runBlocking {
                    val idIndex = retryMongoOperation("read release indexes") { collection.listIndexes<Document>().toList() }
                        .singleOrNull { it["key"] == Document("id", 1) }
                    if (idIndex?.getBoolean("unique", false) != true) {
                        // Keep one copy of each duplicated release so the catalogue stays loadable;
                        // the next publication replaces it.
                        val duplicates = retryMongoOperation("read duplicate release ids") {
                            collection.aggregate<Document>(listOf(
                                Document("\$group", Document("_id", "\$id")
                                    .append("keep", Document("\$max", "\$_id"))
                                    .append("count", Document("\$sum", 1))),
                                Document("\$match", Document("count", Document("\$gt", 1)))
                            )).toList()
                        }
                        for (duplicate in duplicates) {
                            val removed = retryMongoOperation("delete duplicate releases") {
                                collection.deleteMany(Filters.and(
                                    Filters.eq("id", duplicate["_id"]),
                                    Filters.ne("_id", duplicate["keep"])
                                )).deletedCount
                            }
                            LOGGER.warn("Removed {} duplicate copies of release {}", removed, duplicate["_id"])
                        }
                        if (idIndex != null) retryMongoOperation("drop nonunique release id index") {
                            try {
                                collection.dropIndex(idIndex.getString("name"))
                            } catch (e: MongoCommandException) {
                                // A retry after a lost acknowledgement finds the index already dropped
                                if (e.errorCode != INDEX_NOT_FOUND) throw e
                            }
                        }
                    }
                    retryMongoOperation("create release id index") {
                        collection.createIndex(Document("id", 1), IndexOptions().unique(true))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException ||
                    net.adoptium.api.v3.config.APIConfig.DEPLOYMENT_TYPE == net.adoptium.api.v3.config.DeploymentType.UPDATER
                ) throw e
                LOGGER.warn("Failed to create index for releases: ${e.message}")
            }
        }
    )
    private var cdxasCollection: MongoCollection<Cdxa> = createCollection(mongoClient.getDatabase(), CDXAS_DB)
    private val gitHubStatsCollection: MongoCollection<GitHubDownloadStatsDbEntry> = createCollection(mongoClient.getDatabase(), GITHUB_STATS_DB)
    private val dockerStatsCollection: MongoCollection<DockerDownloadStatsDbEntry> = createCollection(mongoClient.getDatabase(), DOCKER_STATS_DB)
    private val packageStatsCollection: MongoCollection<CloudflarePackageDownloadStatsDbEntry> = createCollection(
        mongoClient.getDatabase(),
        PACKAGE_STATS_DB,
        ensureIndexes = { collection ->
            // Create indexes for packageStats if they don't exist
            try {
                runBlocking {
                    retryMongoOperation("create package statistics date index") {
                        collection.createIndex(Document("date", 1))
                    }
                    retryMongoOperation("create package statistics version index") {
                        collection.createIndex(Document("feature_version", 1).append("date", 1))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LOGGER.warn("Failed to create indexes for packageStats: ${e.message}")
            }
        })

    private val releaseInfoCollection: MongoCollection<ReleaseInfo> = createCollection(mongoClient.getDatabase(), RELEASE_INFO_DB)
    private val updateTimeCollection: MongoCollection<UpdatedInfo> = createCollection(mongoClient.getDatabase(), UPDATE_TIME_DB)
    private val cdxaUpdateTimeCollection: MongoCollection<UpdatedInfo> = createCollection(mongoClient.getDatabase(), CDXAS_UPDATE_TIME_DB)
    private val githubReleaseNotesCollection: MongoCollection<ReleaseNotes> = createCollection(
        mongoClient.getDatabase(),
        GH_RELEASE_NOTES,
        ensureIndexes = { collection ->
            try {
                runBlocking {
                    retryMongoOperation("create release notes id index") {
                        collection.createIndex(Document("id", 1), IndexOptions().unique(true))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                LOGGER.warn("Failed to create index for releaseNotes: ${e.message}")
            }
        }
    )
    private val client: MongoClient = mongoClient
    private val initialUpdateInfo = UpdatedInfo(TimeSource.now().minusMinutes(5), "000", 0)

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
        const val GH_RELEASE_METADATA = "githubReleaseMetadata"
        const val RELEASE_DB = "release"
        const val CDXAS_DB = "cdxas"
        const val CDXAS_UPDATE_TIME_DB = "cdxasUpdateTime"
        const val GITHUB_STATS_DB = "githubStats"
        const val DOCKER_STATS_DB = "dockerStats"
        const val PACKAGE_STATS_DB = "packageStats"
        const val RELEASE_INFO_DB = "releaseInfo"
        const val UPDATE_TIME_DB = "updateTime"
        const val GH_RELEASE_NOTES = "releaseNotes"
        private const val INDEX_NOT_FOUND = 27
    }

    override suspend fun updateAllRepos(repos: AdoptRepos, checksum: String) {
        // Writes never remove a release before its replacement exists, so the database is
        // usable at every point and a failure leaves old and new releases, each complete.
        // The published generation only advances once every feature version is written.
        repos.repos.forEach { (version, releases) ->
            writeReleases(version, releases)
        }
        updateUpdatedTime(TimeSource.now(), checksum, repos.hashCode())
    }

    override suspend fun updateCdxaRepos(repo: AdoptCdxaRepos, checksum: String) {
        val featureVersions = repo.repos.map { it.featureVersion }.toSet()
        featureVersions.forEach { featureVersion ->
            writeCdxas(featureVersion, repo.repos.filter { it.featureVersion == featureVersion })
        }
        removeCdxasNotInFeatureVersions(featureVersions)
        updateCdxaUpdatedTime(TimeSource.now(), checksum, repo.hashCode(), repo.lastModified)
    }

    private suspend fun writeReleases(featureVersion: Int, value: FeatureRelease) {
        val toAdd = value.releases.getReleases().toList()
        // Empty features are not whole-version deletion requests; see ADR 8.
        if (toAdd.isNotEmpty()) {
            retryMongoOperation("upsert releases for feature $featureVersion") {
                releasesCollection.bulkWrite(
                    toAdd.map { ReplaceOneModel(Document("id", it.id), it, ReplaceOptions().upsert(true)) },
                    BulkWriteOptions().ordered(false)
                )
            }
            retryMongoOperation("delete superseded releases for feature $featureVersion") {
                releasesCollection.deleteMany(
                    Filters.and(majorVersionMatcher(featureVersion), Filters.nin("id", toAdd.map { it.id }))
                )
            }
        }
    }

    private suspend fun writeCdxas(featureVersion: Int, cdxas: List<Cdxa>) {
        // First delete all existing for featureVersion
        retryMongoOperation("delete attestations for feature $featureVersion") {
            cdxasCollection.deleteMany(cdxaFeatureVersionMatcher(featureVersion))
        }
        if (cdxas.isNotEmpty()) {
            // Then add updated featureVersionCdxas...
            cdxasCollection.insertMany(cdxas, InsertManyOptions())
        }
    }

    private suspend fun removeCdxasNotInFeatureVersions(featureVersions: Set<Int>) {
        // Get existing featureVersions in DB
        val dbFeatureVersions = retryMongoOperation("read attestation feature versions") {
            cdxasCollection.distinct<Int>("featureVersion").toList()
        }

        // Delete feature versions that no longer exist
        val toDelete = dbFeatureVersions.filterNot { it in featureVersions }
        toDelete.forEach { featureVersionToDelete ->
            // Delete all existing for featureVersionToDelete
            retryMongoOperation("delete attestations for feature $featureVersionToDelete") {
                cdxasCollection.deleteMany(cdxaFeatureVersionMatcher(featureVersionToDelete))
            }
        }
    }

    override suspend fun readReleaseData(featureVersion: Int): FeatureRelease {
        val releases = retryMongoOperation("read releases for feature $featureVersion") {
            releasesCollection.find(majorVersionMatcher(featureVersion)).toList()
        }

        return FeatureRelease(featureVersion, Releases(releases))
    }

    override suspend fun readAllReleaseData(): AdoptRepos {
        val releases = retryMongoOperation("read all releases") { releasesCollection.find().toList() }
        return AdoptRepos(releases
            .groupBy { it.version_data.major }
            .toSortedMap()
            .map { (version, releases) -> FeatureRelease(version, Releases(releases)) })
    }

    override suspend fun readCdxaData(): List<Cdxa> {
        return retryMongoOperation("read attestations") {
            cdxasCollection.find(Document()).toList()
        }
    }

    override suspend fun addGithubDownloadStatsEntries(stats: List<GitHubDownloadStatsDbEntry>) {
        gitHubStatsCollection.insertMany(stats)
    }

    override suspend fun getStatsForFeatureVersion(featureVersion: Int): List<GitHubDownloadStatsDbEntry> {
        return retryMongoOperation("read GitHub statistics for feature $featureVersion") {
            gitHubStatsCollection.find(Document("version.major", featureVersion)).toList()
        }
    }

    override suspend fun getLatestGithubStatsForFeatureVersion(featureVersion: Int): GitHubDownloadStatsDbEntry? {
        return retryMongoOperation("read latest GitHub statistics for feature $featureVersion") {
            gitHubStatsCollection
                .find(Document("feature_version", featureVersion))
                .sort(Document("date", -1))
                .limit(1)
                .firstOrNull()
        }
    }

    override suspend fun getGithubStats(start: ZonedDateTime, end: ZonedDateTime): List<GitHubDownloadStatsDbEntry> {
        return retryMongoOperation("read GitHub statistics") {
            gitHubStatsCollection.find(betweenDates(start, end)).sort(Document("date", 1)).toList()
        }
    }

    override suspend fun getDockerStats(start: ZonedDateTime, end: ZonedDateTime): List<DockerDownloadStatsDbEntry> {
        return retryMongoOperation("read Docker statistics") {
            dockerStatsCollection.find(betweenDates(start, end)).sort(Document("date", 1)).toList()
        }
    }

    override suspend fun addDockerDownloadStatsEntries(stats: List<DockerDownloadStatsDbEntry>) {
        dockerStatsCollection.insertMany(stats)
    }

    override suspend fun getLatestAllDockerStats(): List<DockerDownloadStatsDbEntry> {

        val repoNames = retryMongoOperation("read Docker statistics repositories") {
            dockerStatsCollection.distinct<String>("repo").toList()
        }

        return repoNames
            .map {
                retryMongoOperation("read latest Docker statistics") {
                    dockerStatsCollection.find(Document("repo", it)).sort(Document("date", -1)).limit(1).first()
                }
            }
            .toList()
    }

    override suspend fun addPackageDownloadStatsEntries(stats: List<CloudflarePackageDownloadStatsDbEntry>) {
        packageStatsCollection.insertMany(stats)
    }

    override suspend fun getAggregatedPackageStats(start: ZonedDateTime, end: ZonedDateTime): List<CloudflarePackageDownloadStatsDbEntry> {
        val pipeline = listOf(
            Document(
                "\$match",
                betweenDates(start, end)
            ),
            Document(
                "\$group",
                mapOf(
                    "_id" to Document("feature_version", "\$feature_version"),
                    "downloads" to Document("\$sum", "\$downloads"),
                    "date" to Document("\$max", "\$date")
                )
            ),
            Document(
                "\$project",
                mapOf(
                    "_id" to 0,
                    "date" to 1,
                    "downloads" to 1,
                    "feature_version" to "\$_id.feature_version"
                )
            )
        )

        return retryMongoOperation("read aggregated package statistics") {
            packageStatsCollection.aggregate(pipeline).toList()
        }

    }

    override suspend fun getLatestPackageStatsForFeatureVersion(featureVersion: Int): CloudflarePackageDownloadStatsDbEntry? {
        return retryMongoOperation("read latest package statistics for feature $featureVersion") {
            packageStatsCollection
                .find(Document("feature_version", featureVersion))
                .sort(Document("date", -1))
                .limit(1)
                .firstOrNull()
        }
    }

    override suspend fun getPackageStats(start: ZonedDateTime, end: ZonedDateTime): List<CloudflarePackageDownloadStatsDbEntry> {
        return retryMongoOperation("read package statistics") {
            packageStatsCollection.find(betweenDates(start, end)).sort(Document("date", 1)).toList()
        }
    }

    override suspend fun removeStatsBetween(start: ZonedDateTime, end: ZonedDateTime) {
        val deleteQuery = betweenDates(start, end)
        retryMongoOperation("delete Docker statistics") { dockerStatsCollection.deleteMany(deleteQuery) }
        retryMongoOperation("delete GitHub statistics") { gitHubStatsCollection.deleteMany(deleteQuery) }
        retryMongoOperation("delete package statistics") { packageStatsCollection.deleteMany(deleteQuery) }
    }

    override suspend fun setReleaseInfo(releaseInfo: ReleaseInfo) {
        retryMongoOperation("delete release information") {
            releaseInfoCollection.deleteMany(releaseVersionDbEntryMatcher())
        }
        retryMongoOperation("replace release information") {
            releaseInfoCollection.replaceOne(releaseVersionDbEntryMatcher(), releaseInfo, ReplaceOptions().upsert(true))
        }
    }

    // visible for testing
    open suspend fun updateUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int) {
        val update = UpdatedInfo(dateTime, checksum, hashCode, generationId = UUID.randomUUID().toString())
        retryMongoOperation("replace release generation") {
            updateTimeCollection.replaceOne(Document(), update, ReplaceOptions().upsert(true))
        }
        retryMongoOperation("delete obsolete release generations") {
            updateTimeCollection.deleteMany(Document("time", BsonDocument("\$lt", BsonDateTime(dateTime.toInstant().toEpochMilli()))))
        }
    }

    open suspend fun updateCdxaUpdatedTime(dateTime: ZonedDateTime, checksum: String, hashCode: Int, lastModified: Instant? = null) {
        val lastModifiedDate = lastModified?.let { java.util.Date.from(it) }
        retryMongoOperation("replace attestation generation") {
            cdxaUpdateTimeCollection.replaceOne(Document(), UpdatedInfo(dateTime, checksum, hashCode, lastModifiedDate), ReplaceOptions().upsert(true))
        }
        retryMongoOperation("delete obsolete attestation generations") {
            cdxaUpdateTimeCollection.deleteMany(Document("time", BsonDocument("\$lt", BsonDateTime(dateTime.toInstant().toEpochMilli()))))
        }
    }

    override suspend fun getUpdatedAt(): UpdatedInfo {
        val info = retryMongoOperation("read release generation") { updateTimeCollection.find().firstOrNull() }
        // if we have no existing time, make it 5 mins ago, should only happen on first time the db is used
        return info ?: initialUpdateInfo
    }

    override suspend fun getCdxaUpdatedAt(): UpdatedInfo {
        val info = retryMongoOperation("read attestation generation") { cdxaUpdateTimeCollection.find().firstOrNull() }
        // if we have no existing time, make it 5 mins ago, should only happen on first time the db is used
        return info ?: UpdatedInfo(TimeSource.now().minusMinutes(5), "000", 0)
    }

    override suspend fun getReleaseInfo(): ReleaseInfo? {
        return retryMongoOperation("read release information") {
            releaseInfoCollection.find(releaseVersionDbEntryMatcher()).firstOrNull()
        }
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
    private fun cdxaFeatureVersionMatcher(featureVersion: Int) = Document("featureVersion", featureVersion)

    override suspend fun getGhReleaseMetadata(gitHubId: GitHubId): GHReleaseMetadata? {
        return retryMongoOperation("read GitHub release metadata") {
            githubReleaseMetadataCollection.find(matchGithubId(gitHubId)).firstOrNull()
        }
    }

    override suspend fun setGhReleaseMetadata(ghReleaseMetadata: GHReleaseMetadata) {
        retryMongoOperation("replace GitHub release metadata") {
            githubReleaseMetadataCollection.replaceOne(
                matchGithubId(ghReleaseMetadata.gitHubId), ghReleaseMetadata, ReplaceOptions().upsert(true))
        }
    }

    override suspend fun hasReleaseNotesForGithubId(gitHubId: GitHubId): Boolean {
        return retryMongoOperation("check release notes") {
            githubReleaseNotesCollection.find(Document("id", gitHubId.id)).firstOrNull() != null
        }
    }

    override suspend fun putReleaseNote(releaseNotes: ReleaseNotes) {
        retryMongoOperation("replace release notes") {
            githubReleaseNotesCollection.replaceOne(
                Document("id", releaseNotes.id),
                releaseNotes,
                ReplaceOptions().upsert(true)
            )
        }
    }

    override suspend fun getReleaseNotes(vendor: Vendor, releaseName: String): ReleaseNotes? {
        val query = Document(
            "\$and",
            BsonArray(
                listOf(
                    BsonDocument("release_name", BsonString(releaseName)),
                    BsonDocument("vendor", BsonString(vendor.name)),
                )
            )
        )
        return retryMongoOperation("read release notes") {
            githubReleaseNotesCollection.find(query).firstOrNull()
        }
    }

    override suspend fun isConnected(): Boolean {
        return retryMongoOperation("check database connection") {
            client.getDatabase().runCommand(Document("serverStatus", 1)).getDouble("uptime") >= 0
        }
    }

    private fun matchGithubId(gitHubId: GitHubId) = Document("gitHubId.id", gitHubId.id)
}
