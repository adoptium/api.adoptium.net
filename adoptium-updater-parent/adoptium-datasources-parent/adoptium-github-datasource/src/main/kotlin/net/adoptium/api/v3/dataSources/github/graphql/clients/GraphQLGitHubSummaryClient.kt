package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubSummaryClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {


    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
        val query = GetReleaseSummary(owner, repoName)

        LOGGER.info("Getting repo summary $repoName")

        val releases = graphQLGitHubInterface.getAll(
            query::withCursor,
            { request -> getSummary(request) },
            { it.repository!!.releases.pageInfo.hasNextPage },
            { it.repository!!.releases.pageInfo.endCursor }
        )

        LOGGER.info("Done getting summary $repoName")

        return GHRepositorySummary(GHReleasesSummary(releases, PageInfo(false, null)))
    }

    private fun getSummary(request: QuerySummaryData): List<GHReleaseSummary> {
        if (request.repository == null) return listOf()

        // nested releases based on how we deserialise githubs data
        return request.repository.releases.releases
    }

    class GetReleaseSummary(private val owner: String, private val repoName: String, override val variables: Any = mapOf<String, String>()) :
        GraphQLClientRequest<QuerySummaryData> {

        fun withCursor(cursor: String?): GetReleaseSummary {
            return if (cursor != null) GetReleaseSummary(owner, repoName, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() =
                """
                        query(${'$'}cursorPointer:String) {
                            repository(owner:"$owner", name:"$repoName") {
                                releases(first:50, after:${'$'}cursorPointer, orderBy: {field: CREATED_AT, direction: DESC}) {
                                    nodes {
                                        id,
                                        publishedAt,
                                        updatedAt,
                                        name,
                                        releaseAssets {
                                            totalCount
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

        override fun responseType(): KClass<QuerySummaryData> {
            return QuerySummaryData::class
        }
    }
}
