package net.adoptopenjdk.api.v3

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.adoptopenjdk.api.v3.dataSources.github.GitHubApi
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepo
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import net.adoptopenjdk.api.v3.mapping.ReleaseMapper
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptopenjdk.api.v3.mapping.adopt.SemeruReleaseMapperFactory
import net.adoptopenjdk.api.v3.mapping.upstream.UpstreamReleaseMapper
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.Vendor
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

interface AdoptRepository {
    suspend fun getRelease(version: Int): FeatureRelease?
    suspend fun getSummary(version: Int): GHRepositorySummary
    suspend fun getReleaseById(id: GitHubId): ReleaseResult?
}

@Singleton
class AdoptRepositoryImpl @Inject constructor(
    val client: GitHubApi,
    adoptReleaseMapperFactory: AdoptReleaseMapperFactory,
    semeruReleaseMapperFactory: SemeruReleaseMapperFactory
) : AdoptRepository {

    companion object {
        const val ADOPT_ORG = "AdoptOpenJDK"
        const val ADOPTIUM_ORG = "adoptium"

        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    private val mappers = mapOf(
        ".*/openjdk\\d+-openj9-releases/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-openj9-nightly/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-nightly/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),
        ".*/openjdk\\d+-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptopenjdk),

        ".*/openjdk\\d+-upstream-binaries/.*".toRegex() to UpstreamReleaseMapper,

        ".*/openjdk\\d+-dragonwell-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.alibaba),

        ".*/temurin\\d+-binaries/.*".toRegex() to adoptReleaseMapperFactory.get(Vendor.adoptium),

        ".*/semeru\\d+-binaries/.*".toRegex() to semeruReleaseMapperFactory.get(),
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
        if (release == null) {
            return null
        }
        return getMapperForRepo(release.url)
            .toAdoptRelease(release)
    }

    override suspend fun getRelease(version: Int): FeatureRelease {
        val repo = getDataForEachRepo(version, ::getRepository)
            .await()
            .filterNotNull()
            .map { AdoptRepo(it) }
        return FeatureRelease(version, repo)
    }

    override suspend fun getSummary(version: Int): GHRepositorySummary {
        val releaseSummaries = getDataForEachRepo(version) { owner: String, repoName: String -> client.getRepositorySummary(owner, repoName) }
            .await()
            .filterNotNull()
            .flatMap { it.releases.releases }
            .toList()
        return GHRepositorySummary(GHReleasesSummary(releaseSummaries, PageInfo(false, "")))
    }

    private suspend fun getRepository(owner: String, repoName: String): List<Release> {
        return client
            .getRepository(owner, repoName)
            .getReleases()
            .flatMap {
                try {
                    val result = getMapperForRepo(it.url).toAdoptRelease(it)
                    if (result.succeeded()) {
                        result.result!!
                    } else {
                        return@flatMap emptyList<Release>()
                    }
                } catch (e: Exception) {
                    return@flatMap emptyList<Release>()
                }
            }
    }

    private suspend fun <E> getDataForEachRepo(version: Int, getFun: suspend (String, String) -> E): Deferred<List<E?>> {
        LOGGER.info("getting $version")
        return GlobalScope.async {

            return@async listOf(
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-openj9-releases", getFun),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-openj9-nightly", getFun),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-nightly", getFun),
                getRepoDataAsync(ADOPT_ORG, Vendor.adoptopenjdk, "openjdk$version-binaries", getFun),

                getRepoDataAsync(ADOPT_ORG, Vendor.openjdk, "openjdk$version-upstream-binaries", getFun),

                getRepoDataAsync(ADOPT_ORG, Vendor.alibaba, "openjdk$version-dragonwell-binaries", getFun),

                getRepoDataAsync(ADOPTIUM_ORG, Vendor.adoptium, "temurin$version-binaries", getFun),

                getRepoDataAsync(ADOPT_ORG, Vendor.ibm, "semeru$version-binaries", getFun)
            )
                .map { repo -> repo.await() }
        }
    }

    private fun <E> getRepoDataAsync(owner: String, vendor: Vendor, repoName: String, getFun: suspend (String, String) -> E): Deferred<E?> {
        return GlobalScope.async {
            if (!Vendor.validVendor(vendor)) {
                return@async null
            }
            LOGGER.info("getting $owner $repoName")
            val releases = getFun(owner, repoName)
            LOGGER.info("Done getting $owner $repoName")
            return@async releases
        }
    }
}
