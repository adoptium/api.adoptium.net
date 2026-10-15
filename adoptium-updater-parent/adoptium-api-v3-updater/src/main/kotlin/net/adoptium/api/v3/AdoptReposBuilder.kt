package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.GHReleaseMetadata
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

@ApplicationScoped
class AdoptReposBuilder @Inject constructor(
    private var adoptRepository: AdoptRepository,
    private var versionSupplier: VersionSupplier
    ) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val excluded: MutableSet<GitHubId> = HashSet()

    suspend fun incrementalUpdate(
        toUpdate: Set<String>,
        repo: AdoptRepos,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?,
    ): AdoptRepos {
        val updated = repo
            .repos
            .map { entry -> getUpdatedFeatureRelease(toUpdate, entry, repo, gitHubMetadataSupplier) }

        return AdoptRepos(updated)
    }

    private suspend fun getUpdatedFeatureRelease(
        toUpdate: Set<String>,
        entry: Map.Entry<Int, FeatureRelease>,
        repo: AdoptRepos,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?,
    ): FeatureRelease {
        val summary = adoptRepository.getSummary(entry.key)

        // Update cycle
        // 1) remove missing ones
        // 2) add new ones
        // 3) fix updated
        val existingRelease = repo.getFeatureRelease(entry.key)
        return if (existingRelease != null) {
            val ids = summary.releases.getIds()

            // keep only release ids that still exist
            val pruned = existingRelease.retain(ids)

            // Find newly added releases
            val newReleases = getNewReleases(summary, pruned)
            val updatedReleases = getUpdatedReleases(summary, pruned)
            val binaryCountChanged = binaryCountHasChanged(summary, gitHubMetadataSupplier)
            val youngReleases = getYoungReleases(summary, pruned)
            val explicitlyAdded = getExplicitlyAddedReleases(summary, toUpdate)

            val selectedSummaries = newReleases +
                updatedReleases +
                youngReleases +
                explicitlyAdded +
                binaryCountChanged
            val selectedReleases = selectedSummaries.distinctBy { it.id }.flatMap { getReleaseById(it) }
            val refreshedReleases = selectedReleases + getLatestEaReleases(
                summary, pruned, selectedReleases, selectedSummaries.map { it.id }.toSet()
            )
            val refreshedIds = refreshedReleases.map { GitHubId(it.id) }.toSet()

            // A rolling release can acquire new version-based split IDs. Replace all
            // old snapshots of each refreshed GitHub release, not just matching split IDs.
            FeatureRelease(
                entry.key,
                Releases(
                    pruned.releases.nodes.filterValues { GitHubId(it.id) !in refreshedIds }
                )
            )
                .add(refreshedReleases)
        } else {
            val newReleases = getNewReleases(summary, FeatureRelease(entry.key, emptyList()))
            FeatureRelease(entry.key, Releases(newReleases.distinctBy { it.id }.flatMap { getReleaseById(it) }))
        }
    }

    private fun getUpdatedReleases(summary: GHRepositorySummary, pruned: FeatureRelease): List<GHReleaseSummary> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter { pruned.releases.hasReleaseBeenUpdated(it.id, it.getUpdatedTime()) }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
    }

    private suspend fun getLatestEaReleases(
        summary: GHRepositorySummary,
        pruned: FeatureRelease,
        selectedReleases: List<Release>,
        selectedIds: Set<GitHubId>
    ): List<Release> {
        val latestEaIds = (pruned.releases.nodes.values + selectedReleases)
            .filter { it.release_type == ReleaseType.ea }
            .groupBy { it.vendor to it.release_link.substringBefore("/releases/") }
            .values
            .map { releases ->
                val latest = releases.maxWith(compareBy<Release> { it.timestamp.dateTime.toInstant() }.thenBy { it.id })
                GitHubId(latest.id)
            }
            .toSet()
        // Replacing an asset need not change its parent release's timestamp or count.
        // Always check the latest published EA per repository and feature version;
        // getYoungReleases already covers releases published in the last seven days.
        return summary.releases.releases
            .filter { it.id in latestEaIds && it.id !in selectedIds && it.id !in excluded }
            .filter { isReleaseOldEnough(it.publishedAt) }
            .distinctBy { it.id }
            .flatMap { getReleaseById(it) }
    }

    private suspend fun binaryCountHasChanged(
        summary: GHRepositorySummary,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?
    ): List<GHReleaseSummary> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter {
                gitHubMetadataSupplier(it.id)?.totalBinaryCount?.equals(it.releaseAssets.totalCount)?.not() ?: false
            }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
    }

    private fun getYoungReleases(summary: GHRepositorySummary, pruned: FeatureRelease): List<GHReleaseSummary> {
        val eaIds = pruned.releases.nodes.values
            .filter { it.release_type == ReleaseType.ea }
            .map { GitHubId(it.id) }
            .toSet()
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter {
                // Re-pull releases published in the last week. Only GA releases also
                // use recent edits: otherwise edits can re-enable the entire EA history.
                ChronoUnit.DAYS.between(it.getPublishedTime(), TimeSource.now()).absoluteValue < 7 ||
                    (it.id !in eaIds && ChronoUnit.DAYS.between(it.getUpdatedTime(), TimeSource.now()).absoluteValue < 7)
            }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
    }

    /**
     * Fetches releases from github that have been explicitly requested by the user for an update
     */
    private fun getExplicitlyAddedReleases(summary: GHRepositorySummary, releaseNames: Set<String>): List<GHReleaseSummary> {
        return releaseNames
            .flatMap { name ->
                val allReleases = summary.releases.releases
                val releases = allReleases
                    .filter { !excluded.contains(it.id) } // Filter out excluded releases
                    .filter { name == it.name } // Find release with the requested name
                    .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
                    .onEach {
                        LOGGER.info("Updating ${it.name}")
                    }

                if (releases.isEmpty()) {
                    LOGGER.warn("Failed to match update name $name")
                }
                releases
            }
    }

    private fun getNewReleases(summary: GHRepositorySummary, currentRelease: FeatureRelease): List<GHReleaseSummary> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter { !currentRelease.releases.hasReleaseId(it.id) }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
    }

    private fun isReleaseOldEnough(timestamp: String): Boolean {
        val created = ReleaseMapper.parseDate(timestamp)
        return ChronoUnit.MINUTES.between(created, TimeSource.now()).absoluteValue > 10
    }

    private suspend fun getReleaseById(it: GHReleaseSummary): List<Release> {
        val result = adoptRepository.getReleaseById(it.id)
        return if (result != null && result.succeeded()) {
            result.result!!
        } else {
            LOGGER.info("Excluding ${it.id.id} from update")
            excluded.add(it.id)
            emptyList()
        }
    }

    suspend fun build(filter: ReleaseIncludeFilter): AdoptRepos {
        excluded.clear()
        // Fetch repos in parallel
        val reposMap = versionSupplier
            .getAllVersions()
            .reversed()
            .mapNotNull { version ->
                adoptRepository.getRelease(version, filter)
            }
            .associateBy { it.featureVersion }
        LOGGER.info("DONE")
        return AdoptRepos(reposMap)
    }
}
