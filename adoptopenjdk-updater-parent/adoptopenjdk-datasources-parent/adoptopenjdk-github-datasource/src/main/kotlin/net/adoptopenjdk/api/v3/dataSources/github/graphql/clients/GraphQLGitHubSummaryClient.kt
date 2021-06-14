package net.adoptopenjdk.api.v3.dataSources.github.graphql.clients

import io.aexp.nodes.graphql.GraphQLRequestEntity
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQLGitHubSummaryClient @Inject constructor(
    graphQLRequest: GraphQLRequest,
    updaterHtmlClient: UpdaterHtmlClient
) : GraphQLGitHubInterface(graphQLRequest, updaterHtmlClient) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
        val requestEntityBuilder = getReleaseSummary(owner, repoName)

        LOGGER.info("Getting repo summary $repoName")

        val releases = getAll(
            requestEntityBuilder,
            { request -> getSummary(request) },
            { it.repository!!.releases.pageInfo.hasNextPage },
            { it.repository!!.releases.pageInfo.endCursor },
            clazz = QuerySummaryData::class.java
        )

        LOGGER.info("Done getting summary $repoName")

        return GHRepositorySummary(GHReleasesSummary(releases, PageInfo(false, null)))
    }

    private fun getSummary(request: QuerySummaryData): List<GHReleaseSummary> {
        if (request.repository == null) return listOf()

        // nested releases based on how we deserialise githubs data
        return request.repository.releases.releases
    }

    private fun getReleaseSummary(owner: String, repoName: String): GraphQLRequestEntity.RequestBuilder {
        return request(
            """
                        query(${'$'}cursorPointer:String) { 
                            repository(owner:"$owner", name:"$repoName") { 
                                releases(first:50, after:${'$'}cursorPointer, orderBy: {field: CREATED_AT, direction: DESC}) {
                                    nodes {
                                        id,
                                        publishedAt,
                                        updatedAt
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
        )
    }
}
