package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubReleaseClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface,
    private val graphQLGitHubReleaseRequest: GraphQLGitHubReleaseRequest
) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getReleaseById(id: GitHubId): GHRelease? {

        LOGGER.info("Getting id $id")

        val query = RequestReleaseById(id)

        val result = graphQLGitHubInterface.queryApi(query::withCursor, null)

        val release: GHRelease = if (result?.data?.release?.releaseAssets?.pageInfo?.hasNextPage == true) {
            graphQLGitHubReleaseRequest.getAllReleaseAssets(result.data!!.release)
        } else {
            if (result?.data == null) {
                return null
            }
            result.data!!.release
        }

        return release
    }

    class RequestReleaseById(private val releaseId: GitHubId, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHReleaseResult> {
        fun withCursor(cursor: String?): RequestReleaseById {
            return if (cursor != null) RequestReleaseById(releaseId, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() =
                """query {
                              node(id:"${releaseId.id}") {
                                ... on Release {
                                        id,
                                        url,
                                        name, 
                                        publishedAt,
                                        updatedAt,
                                        isPrerelease,
                                        resourcePath,
                                        releaseAssets(first:50) {
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
                                    }
                            }
                            rateLimit {
                                cost,
                                remaining
                            }
                        }
                    """

        override fun responseType(): KClass<GHReleaseResult> {
            return GHReleaseResult::class
        }
    }
}
