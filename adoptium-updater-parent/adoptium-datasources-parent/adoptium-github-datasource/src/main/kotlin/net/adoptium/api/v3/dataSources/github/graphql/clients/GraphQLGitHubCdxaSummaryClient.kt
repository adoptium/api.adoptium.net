package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryData
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubCdxaSummaryClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {

        LOGGER.debug("Getting tree file summary of cdxas github repository $org/$repo/$directory")

        val query = RequestCdxaRepoSummary(org, repo, directory)

        val result = try {
          graphQLGitHubInterface.queryApi(query::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on cdxa summary query $org/$repo/$directory :"+e)
            return null
        }

        val ghCdxaRepoSummary: GHCdxaRepoSummaryData? = try {
          if (result == null || result?.data == null) {
            return null
          } else {
            result.data
          }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping cdxa summary query response $org/$repo/$directory :"+e+" query result: "+result)
            return null
        }

        return ghCdxaRepoSummary
    }

    class RequestCdxaRepoSummary(val org: String, val repo: String, val directory: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHCdxaRepoSummaryData> {
        fun withCursor(cursor: String?): RequestCdxaRepoSummary {
            return if (cursor != null) RequestCdxaRepoSummary(org, repo, directory, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() =
                """
    query {
      repository(owner: "${org}", name: "${repo}") {
        defaultBranchRef {
          target {
            ... on Commit {
              history(first: 1, path: "./${directory}") {
                nodes {
                  committedDate
                }
              }
            }
          }
        }
        object(expression: "HEAD:${directory}") {
          ... on Tree {
            entries {
              name
              type
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

        override fun responseType(): KClass<GHCdxaRepoSummaryData> {
            return GHCdxaRepoSummaryData::class
        }
    }
}

