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

    open suspend fun getAttestationSummary(org: String, repo: String): GHAttestationRepoSummaryData? {

        LOGGER.debug("Getting tree file summary of attestations github repository $org/$repo")

        val query = RequestAttestationRepoSummary(org, repo)

        val result = graphQLGitHubInterface.queryApi(query::withCursor, null)

        val ghAttestationRepoSummary: GHAttestationRepoSummaryData? = if (result?.data == null) {
            return null
        } else {
            result.data
        }

        return ghAttestationRepoSummary
    }

    class RequestAttestationRepoSummary(val org: String, val repo: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHAttestationRepoSummaryData> {
        fun withCursor(cursor: String?): RequestAttestationRepoSummary {
            return if (cursor != null) RequestAttestationRepoSummary(org, repo, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() =
                """
    query {
      repository(owner: "${org}", name: "${repo}") {
        object(expression: "HEAD:") {
          ... on Tree {                         
            entries {                           
              name
              type
              object { 
                ... on Tree {                   
                 entries {                  
                  name                  
                  type              
                 }
                }
              }
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

