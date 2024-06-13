package net.adoptium.api.v3.dataSources.github.graphql.clients

/* ktlint-disable no-wildcard-imports */
/* ktlint-enable no-wildcard-imports */
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.QueryData
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubRepositoryClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface,
    private val graphQLGitHubReleaseRequest: GraphQLGitHubReleaseRequest
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getRepository(
        owner: String,
        repoName: String,
        filter: (updateTime: String, isPrerelease: Boolean) -> Boolean): GHRepository {
        val query = GetQueryData(owner, repoName)

        LOGGER.info("Getting repo $repoName")

        val releases = graphQLGitHubInterface.getAll(
            query::withCursor,
            { request -> getAllAssets(request, filter) },
            { it.repository!!.releases.pageInfo.hasNextPage },
            { it.repository!!.releases.pageInfo.endCursor }
        )

        LOGGER.info("Done getting $repoName")

        return GHRepository(GHReleases(releases, PageInfo(false, null)))
    }

    private suspend fun getAllAssets(request: QueryData, filter: (updateTime: String, isPrerelease: Boolean) -> Boolean): List<GHRelease> {
        if (request.repository == null) return listOf()

        // nested releases based on how we deserialise githubs data
        return request.repository.releases.releases
            .filter {
                val include = filter(it.updatedAt, it.isPrerelease)
                if (!include) {
                    LOGGER.debug("Excluding " + it.url)
                }
                return@filter include
            }
            .map { release ->
                if (release.releaseAssets.pageInfo.hasNextPage) {
                    graphQLGitHubReleaseRequest.getAllReleaseAssets(release)
                } else {
                    release
                }
            }
    }

    class GetQueryData(val owner: String, val repoName: String, override val variables: Any = mapOf<String, String>()) :
        GraphQLClientRequest<QueryData> {

        fun withCursor(cursor: String?): GetQueryData {
            return if (cursor != null) GetQueryData(owner, repoName, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() = """
                        query(${'$'}cursorPointer:String) {
                            repository(owner:"$owner", name:"$repoName") {
                                releases(first:50, after:${'$'}cursorPointer, orderBy: {field: CREATED_AT, direction: DESC}) {
                                    nodes {
                                        id,
                                        url,
                                        name, 
                                        publishedAt,
                                        updatedAt,
                                        isPrerelease,
                                        resourcePath,
                                        releaseAssets(first:1) {
                                            totalCount,
                                            nodes {
                                                downloadCount,
                                                updatedAt,
                                                name,
                                                downloadUrl,
                                                size
                                            },
                                            pageInfo {
                                                hasNextPage,
                                                endCursor
                                            }
                                        }
                                    },
                                    pageInfo {
                                        hasNextPage,
                                        endCursor
                                    }
                                }
                            }
                            rateLimit {
                                cost,
                                remaining
                            }
                        }
                    """
                .trimIndent()
                .replace("\n", "")

        override fun responseType(): KClass<QueryData> {
            return QueryData::class
        }
    }
}
