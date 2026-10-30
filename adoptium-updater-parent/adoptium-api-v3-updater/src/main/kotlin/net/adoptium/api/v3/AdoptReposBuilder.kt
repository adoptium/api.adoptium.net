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

    suspend fun incrementalUpdate(
        toUpdate: Set<String>,
        repo: AdoptRepos,
        gitHubMetadataSupplier: suspend (GitHubId) -> GHReleaseMetadata?,
    ): AdoptRepos {
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

        // Select upstream IDs before fetching: overlapping selectors must not fetch
        // different snapshots of the same rolling release during a single update.
        val selected = summaries.filter { summary ->
            val previous = current[summary.id]
            isReleaseOldEnough(summary.publishedAt) && (
                previous == null ||
                    previous.any { ChronoUnit.SECONDS.between(it.updated_at.dateTime, summary.getUpdatedTime()) != 0L } ||
                    isYoung(summary) || summary.name in toUpdate ||
                    (gitHubMetadataSupplier(summary.id)?.totalBinaryCount?.let { it != summary.releaseAssets.totalCount } ?: false)
                )
        }
        val outcomes = selected.associate { summary ->
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
        return FeatureRelease(entry.key, Releases(pruned.filterValues { GitHubId(it.id) !in replacements }))
            .add(replacements.values.filter { it.succeeded() }.flatMap { it.result!! })
    }

    private fun isYoung(summary: GHReleaseSummary): Boolean =
        ChronoUnit.DAYS.between(summary.getPublishedTime(), TimeSource.now()).absoluteValue < 7 ||
            ChronoUnit.DAYS.between(summary.getUpdatedTime(), TimeSource.now()).absoluteValue < 7

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
        LOGGER.info("DONE")
        return AdoptRepos(reposMap)
    }
}
