package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.models.GHReleaseMetadata
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

    // Only outcomes with no stored splits need extra state. Summary equality includes
    // upstream ID, repository URL, vendor, timestamps, name, prerelease and asset count.
    private data class CompletedOutcome(val summary: GHReleaseSummary, val excluded: Boolean)
    private val completedOutcomes = mutableMapOf<Int, MutableMap<GitHubId, CompletedOutcome>>()

    suspend fun incrementalUpdate(
        toUpdate: Set<String>,
        repo: AdoptRepos,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?,
    ): AdoptRepos {
        completedOutcomes.keys.retainAll(repo.repos.keys)
        val updated = repo
            .repos
            .map { entry -> getUpdatedFeatureRelease(toUpdate, entry, gitHubMetadataSupplier) }

        return AdoptRepos(updated)
    }

    private suspend fun getUpdatedFeatureRelease(
        toUpdate: Set<String>,
        entry: Map.Entry<Int, FeatureRelease>,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?,
    ): FeatureRelease {
        val summaries = adoptRepository.getSummary(entry.key).releases.releases.distinctBy { it.id }
        val ids = summaries.map { it.id }.toSet()
        val pruned = entry.value.releases.nodes.filterValues { GitHubId(it.id) in ids }
        val current = pruned.values.groupBy { GitHubId(it.id) }
        val completed = completedOutcomes.getOrPut(entry.key) { mutableMapOf() }
        val summariesById = summaries.associateBy { it.id }
        completed.entries.removeAll { (id, outcome) ->
            outcome.summary != summariesById[id] || id in current
        }
        fun isEa(summary: GHReleaseSummary): Boolean {
            val previous = current[summary.id]?.firstOrNull()
            val url = summary.url
            val prerelease = summary.isPrerelease
            return if (url != null && prerelease != null) {
                ReleaseMapper.releaseType(summary.name, url, prerelease) == ReleaseType.ea
            } else previous?.release_type == ReleaseType.ea
        }
        val eaIds = summaries.filter(::isEa).map { it.id }.toSet()
        val latestEaIds = summaries.filter { it.id in eaIds }
            .groupBy { summary ->
                val previous = current[summary.id]?.firstOrNull()
                (summary.vendor ?: previous?.vendor) to
                    (summary.url ?: previous?.release_link).orEmpty().substringBefore("/releases/")
            }
            .values.map { group ->
                group.maxWith(compareBy<GHReleaseSummary> { it.getPublishedTime().toInstant() }.thenBy { it.id.id }).id
            }.toSet()

        // Select upstream IDs before fetching: overlapping selectors must not fetch
        // different snapshots of the same rolling release during a single update.
        val selected = summaries.filter { summary ->
            val previous = current[summary.id]
            val outcome = completed[summary.id]
            if (!isReleaseOldEnough(summary.publishedAt)) return@filter false
            if (summary.name in toUpdate) return@filter true
            if (outcome?.excluded == true) return@filter false
            (previous == null && outcome == null) ||
                previous.orEmpty().any { ChronoUnit.SECONDS.between(it.updated_at.dateTime, summary.getUpdatedTime()) != 0L } ||
                summary.id in latestEaIds || isYoung(summary, summary.id in eaIds) ||
                // Completed empty snapshots use their summary's asset count, not stale
                // metadata left over from the removed splits. Summary changes invalidate above.
                (outcome == null && (gitHubMetadataSupplier(summary.id)?.totalBinaryCount
                    ?.let { it != summary.releaseAssets.totalCount } ?: false))
        }
        val outcomes = selected.associate { summary ->
            // A failed retry must not leave an old exclusion/empty result suppressing recovery.
            completed.remove(summary.id)
            val result = try {
                adoptRepository.getReleaseById(summary.id) ?: ReleaseResult(error = "Release unavailable")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOGGER.warn("Failed to refresh ${summary.id.id}", e)
                ReleaseResult(error = "Release fetch failed")
            }
            summary.id to result
        }
        // Only complete snapshots (including empty ones) and explicit permanent
        // exclusions replace all old splits. Retryable failures preserve them.
        val replacements = outcomes.filterValues { it.succeeded() || !it.retryable }
        replacements.forEach { (id, result) ->
            if (!result.succeeded() || result.result!!.isEmpty()) {
                completed[id] = CompletedOutcome(summariesById.getValue(id), !result.succeeded())
            }
        }
        return FeatureRelease(entry.key, Releases(pruned.filterValues { GitHubId(it.id) !in replacements }))
            .add(replacements.values.filter { it.succeeded() }.flatMap { it.result!! })
    }

    private fun isYoung(summary: GHReleaseSummary, isEa: Boolean): Boolean =
        ChronoUnit.DAYS.between(summary.getPublishedTime(), TimeSource.now()).absoluteValue < 7 ||
            (!isEa && ChronoUnit.DAYS.between(summary.getUpdatedTime(), TimeSource.now()).absoluteValue < 7)

    private fun isReleaseOldEnough(timestamp: String): Boolean {
        val created = ReleaseMapper.parseDate(timestamp)
        return ChronoUnit.MINUTES.between(created, TimeSource.now()).absoluteValue > 10
    }

    suspend fun build(filter: ReleaseIncludeFilter): AdoptRepos {
        // Fetch repos in parallel
        val reposMap = versionSupplier
            .getAllVersions()
            .reversed()
            .map { version ->
                checkNotNull(adoptRepository.getRelease(version, filter)) { "Incomplete feature release $version" }
            }
            .associateBy { it.featureVersion }
        // A successful full build starts a new snapshot; failures retain incremental state.
        completedOutcomes.clear()
        LOGGER.info("DONE")
        return AdoptRepos(reposMap)
    }
}
