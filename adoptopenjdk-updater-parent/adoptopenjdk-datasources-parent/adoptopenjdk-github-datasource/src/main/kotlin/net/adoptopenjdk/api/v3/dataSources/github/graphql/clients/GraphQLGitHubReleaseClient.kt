package net.adoptopenjdk.api.v3.dataSources.github.graphql.clients

import io.aexp.nodes.graphql.GraphQLRequestEntity
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQLGitHubReleaseClient @Inject constructor(
    graphQLRequest: GraphQLRequest,
    updaterHtmlClient: UpdaterHtmlClient
) : GraphQLGitHubReleaseRequest(graphQLRequest, updaterHtmlClient) {
    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun getReleaseById(id: GitHubId): GHRelease {
        val requestEntityBuilder = getReleaseByIdQuery(id)

        LOGGER.info("Getting id $id")

        val result = queryApi(requestEntityBuilder, null, GHReleaseResult::class.java)

        val release: GHRelease
        if (result.response.release.releaseAssets.pageInfo.hasNextPage) {
            release = getNextPage(result.response.release)
        } else {
            release = result.response.release
        }

        return release
    }

    private fun getReleaseByIdQuery(releaseId: GitHubId): GraphQLRequestEntity.RequestBuilder {
        return request(
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
        )
    }
}
