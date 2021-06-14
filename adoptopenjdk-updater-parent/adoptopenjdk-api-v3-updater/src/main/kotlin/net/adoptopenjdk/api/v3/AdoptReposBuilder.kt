package net.adoptopenjdk.api.v3

import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.mapping.ReleaseMapper
import net.adoptopenjdk.api.v3.models.Release
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class AdoptReposBuilder @Inject constructor(private var adoptRepository: AdoptRepository) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val excluded: MutableSet<GitHubId> = HashSet()

    suspend fun incrementalUpdate(repo: AdoptRepos): AdoptRepos {
        val updated = repo
            .repos
            .map { entry -> getUpdatedFeatureRelease(entry, repo) }

        return AdoptRepos(updated)
    }

    private suspend fun getUpdatedFeatureRelease(entry: Map.Entry<Int, FeatureRelease>, repo: AdoptRepos): FeatureRelease {
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
            val youngReleases = getYoungReleases(summary)

            pruned
                .add(newReleases)
                .add(updatedReleases)
                .add(youngReleases)
        } else {
            val newReleases = getNewReleases(summary, FeatureRelease(entry.key, emptyList()))
            FeatureRelease(entry.key, Releases(newReleases))
        }
    }

    private suspend fun getUpdatedReleases(summary: GHRepositorySummary, pruned: FeatureRelease): List<Release> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter { pruned.releases.hasReleaseBeenUpdated(it.id, it.getUpdatedTime()) }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
            .flatMap { getReleaseById(it) }
    }

    private suspend fun getYoungReleases(summary: GHRepositorySummary): List<Release> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter {
                // Re-pull data if the release is less than 24h old
                ChronoUnit.HOURS.between(it.getPublishedTime(), TimeSource.now()).absoluteValue < 24 ||
                    ChronoUnit.HOURS.between(it.getUpdatedTime(), TimeSource.now()).absoluteValue < 24
            }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
            .flatMap { getReleaseById(it) }
    }

    private suspend fun getNewReleases(summary: GHRepositorySummary, currentRelease: FeatureRelease): List<Release> {
        return summary.releases.releases
            .filter { !excluded.contains(it.id) }
            .filter { !currentRelease.releases.hasReleaseId(it.id) }
            .filter { isReleaseOldEnough(it.publishedAt) } // Ignore artifacts for the first 10 min while they are still uploading
            .flatMap { getReleaseById(it) }
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
            LOGGER.info("Excluding ${it.id} from update")
            excluded.add(it.id)
            emptyList()
        }
    }

    suspend fun build(versions: Array<Int>): AdoptRepos {
        excluded.clear()
        // Fetch repos in parallel
        val reposMap = versions
            .reversed()
            .mapNotNull { version ->
                adoptRepository.getRelease(version)
            }
            .map { Pair(it.featureVersion, it) }
            .toMap()
        LOGGER.info("DONE")
        return AdoptRepos(reposMap)
    }
}
