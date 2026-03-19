package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationRepoSummaryData
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

@ApplicationScoped
open class GraphQLGitHubAttestationSummaryClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getAttestationSummary(org: String, repo: String, directory: String): GHAttestationRepoSummaryData? {

        LOGGER.debug("Getting tree file summary of attestations github repository $org/$repo/$directory")

        val query = RequestAttestationRepoSummary(org, repo, directory)

        val result = try {
          graphQLGitHubInterface.queryApi(query::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on attestation summary query $org/$repo/$directory :"+e)
            return null
        }

        val ghAttestationRepoSummary: GHAttestationRepoSummaryData? = try {
          if (result == null || result?.data == null) {
            return null
          } else {
            result.data
          }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping attestation summary query response $org/$repo/$directory :"+e+" query result: "+result)
            return null
        }

        return ghAttestationRepoSummary
    }

    class RequestAttestationRepoSummary(val org: String, val repo: String, val directory: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHAttestationRepoSummaryData> {
        fun withCursor(cursor: String?): RequestAttestationRepoSummary {
            return if (cursor != null) RequestAttestationRepoSummary(org, repo, directory, mapOf("cursorPointer" to cursor))
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

        override fun responseType(): KClass<GHAttestationRepoSummaryData> {
            return GHAttestationRepoSummaryData::class
        }
    }
}

