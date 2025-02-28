package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.models.AttestationRepoSummary
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

    open suspend fun getAttestationSummary(org: String, repo: String): AttestationRepoSummary {

        LOGGER.debug("Getting tree file summary of attestations github repository $org/$repo")

        val query = RequestAttestationRepoSummary(org, repo)

        val result: AttestationRepoSummary = graphQLGitHubInterface.queryApi(query, null)

        return result
    }

    class RequestAttestationRepoSummary(org: String, repo: String) : GraphQLClientRequest<AttestationRepoSummary> {
        override val query: String
            get() =
                """
    query {
      repository(owner: "${org}", name: "${repo}") {
        object(expression: HEAD:) {
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

        override fun responseType(): KClass<AttestationRepoSummary> {
            return AttestationRepoSummary::class
        }
    }
}

