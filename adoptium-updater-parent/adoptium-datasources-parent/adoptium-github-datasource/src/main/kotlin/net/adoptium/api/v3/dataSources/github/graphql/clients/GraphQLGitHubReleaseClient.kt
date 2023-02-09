package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class GraphQLGitHubReleaseClient @Inject constructor(
    graphQLRequest: GraphQLRequest,
    updaterHtmlClient: UpdaterHtmlClient
) : GraphQLGitHubReleaseRequest(graphQLRequest, updaterHtmlClient) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun getReleaseById(id: GitHubId): GHRelease? {

        LOGGER.info("Getting id $id")

        val query = RequestReleaseById(id)

        val result = queryApi(query::withCursor, null)

        val release: GHRelease = if (result?.data?.release?.releaseAssets?.pageInfo?.hasNextPage == true) {
            getAllReleaseAssets(result.data!!.release)
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
