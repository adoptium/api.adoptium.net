package net.adoptium.api.v3

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.AdoptRepo
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.mapping.upstream.UpstreamReleaseMapper
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.slf4j.LoggerFactory

interface AdoptRepository {
    suspend fun getRelease(version: Int, filter: ReleaseIncludeFilter): FeatureRelease?
    suspend fun getSummary(version: Int): GHRepositorySummary
    suspend fun getReleaseById(gitHubId: GitHubId): ReleaseResult?
    suspend fun getReleaseFilesForId(gitHubId: GitHubId): List<GHAsset>?

}

@ApplicationScoped
open class AdoptRepositoryImpl @Inject constructor(
    val client: GitHubApi,
    adoptReleaseMapperFactory: AdoptReleaseMapperFactory
) : AdoptRepository {

    companion object {
        const val ADOPT_ORG = "AdoptOpenJDK"
        const val ADOPTIUM_ORG = "adoptium"

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)

        private val EXCLUDED = listOf("jdk17u-2022-05-27-19-32-beta", "jdk-11.0.13'+8")
    }

    private val mappers = mapOf(
        ".*/openjdk\\d+-openj9-releases/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-openj9-nightly/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-nightly/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),

        ".*/openjdk\\d+-upstream-binaries/.*".toRegex() to UpstreamReleaseMapper,

        ".*/openjdk\\d+-dragonwell-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.alibaba),

        ".*/temurin\\d+-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.eclipse),

        ".*/semeru\\d+-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.ibm),
    )

    private fun getMapperForRepo(url: String): ReleaseMapper {
        val mapper = mappers
            .entries
            .firstOrNull { url.matches(it.key) }

        if (mapper == null) {
            throw IllegalStateException("No mapper found for repo $url")
        }

        return mapper.value
    }

    override suspend fun getReleaseById(gitHubId: GitHubId): ReleaseResult? {
        val release = client.getReleaseById(gitHubId)

        if (release == null) return null

        return mapRelease(release)
    }

    private suspend fun mapRelease(release: GHRelease): ReleaseResult {
        // These known malformed upstream tags are permanent policy exclusions,
        // unlike unavailable metadata or a mapping failure that can recover.
        if (release.name in EXCLUDED) {
            return ReleaseResult(error = "Excluded upstream tag ${release.name}", retryable = false)
        }
        return getMapperForRepo(release.url).toAdoptRelease(release)
    }

    override suspend fun getReleaseFilesForId(gitHubId: GitHubId): List<GHAsset>? {
        LOGGER.info("Getting files for " + gitHubId.id)
        return client.getReleaseById(gitHubId)
            ?.releaseAssets
            ?.assets
    }

    override suspend fun getRelease(version: Int, filter: ReleaseIncludeFilter): FeatureRelease {

        val repo = getDataForEachRepo(
            version,
            filter,
            getRepository(filter)
        )
            .await()
            .filterNotNull()
            .map { AdoptRepo(it) }

        return FeatureRelease(version, repo)
    }

    override suspend fun getSummary(version: Int): GHRepositorySummary {
        val releaseSummaries = getDataForEachRepo(
            version,
            ReleaseIncludeFilter.INCLUDE_ALL
        ) { vendor: Vendor, owner: String, repoName: String ->
            val summary = client.getRepositorySummary(owner, repoName)
            summary.copy(releases = summary.releases.copy(releases = summary.releases.releases.map { it.copy(vendor = vendor) }))
        }
            .await()
            .filterNotNull()
            .flatMap { it.releases.releases }
            .toList()
        return GHRepositorySummary(GHReleasesSummary(releaseSummaries, PageInfo(false, "")))
    }

    private fun getRepository(filter: ReleaseIncludeFilter): suspend (Vendor, String, String) -> List<Release> {
        return { vendor: Vendor, owner: String, repoName: String -> getRepository(filter, vendor, owner, repoName) }
    }

    private suspend fun getRepository(filter: ReleaseIncludeFilter, vendor: Vendor, owner: String, repoName: String): List<Release> {
        return client
            .getRepository(owner, repoName) { updatedAt, isPrerelease -> filter.filter(vendor, updatedAt, isPrerelease) }
            .getReleases()
            .flatMap {
                try {
                    val result = mapRelease(it)
                    if (result.succeeded()) {
                        result.result!!
                    } else if (result.retryable) {
                        error("Incomplete snapshot ${it.id.id}: ${result.error}")
                    } else {
                        return@flatMap emptyList<Release>()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A full rebuild has no previous snapshots to merge with.
                    // Abort rather than publishing an incomplete replacement.
                    throw IllegalStateException("Failed to map release ${it.id.id}", e)
                }
            }
    }

    private fun <E> getDataForEachRepo(
        version: Int,
        filter: ReleaseIncludeFilter,
        getFun: suspend (Vendor, String, String) -> E
    ): Deferred<List<E?>> {
        LOGGER.info("getting $version")
        return GlobalScope.async {

            return@async listOf(
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-openj9-releases", getFun, filter),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-openj9-nightly", getFun, filter),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-nightly", getFun, filter),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-binaries", getFun, filter),

                getRepoDataAsync(ADOPT_ORG, Vendor.openjdk, "openjdk$version-upstream-binaries", getFun, filter),

                getRepoDataAsync(ADOPT_ORG, Vendor.alibaba, "openjdk$version-dragonwell-binaries", getFun, filter),

                getRepoDataAsync(ADOPTIUM_ORG, Vendor.eclipse, "temurin$version-binaries", getFun, filter),

                getRepoDataAsync(ADOPT_ORG, Vendor.ibm, "semeru$version-binaries", getFun, filter)
            )
                .map { repo -> repo.await() }
        }
    }

    private fun <E> getRepoDataAsync(
        owner: String,
        vendor: Vendor,
        repoName: String,
        getFun: suspend (Vendor, String, String) -> E,
        filter: ReleaseIncludeFilter
    ): Deferred<E?> {
        return GlobalScope.async {
            if (!Vendor.validVendor(vendor) || !filter.filterVendor(vendor)) {
                return@async null
            }
            LOGGER.info("getting $owner $repoName")
            val releases = getFun(vendor, owner, repoName)
            LOGGER.info("Done getting $owner $repoName")
            return@async releases
        }
    }
}
