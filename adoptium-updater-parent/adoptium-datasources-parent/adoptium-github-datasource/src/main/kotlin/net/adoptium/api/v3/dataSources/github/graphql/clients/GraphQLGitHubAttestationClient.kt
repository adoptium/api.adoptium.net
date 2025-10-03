package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.XmlMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationResponseData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestation
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

import java.time.Instant

@ApplicationScoped
open class GraphQLGitHubAttestationClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getAttestationByName(org: String, repo: String, name: String): GHAttestation? {

        LOGGER.debug("Getting attestation $org/$repo/$name")

        val query = RequestAttestationFileByName(org, repo, name)
        val result = try {
            graphQLGitHubInterface.queryApi(query::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on attestation query $org/$repo/$name :"+e)
            return null
        }

        val ghAttestationResponse: GHAttestationResponseData? = try {
          if (result == null || result?.data == null) {
            return null
          } else {
            result.data
          }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping attestation query response $org/$repo/$name :"+e+" query result: "+result)
            return null
        }

        var committedDate: Instant? = try {
            if ( ghAttestationResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate != null ) {
              Instant.parse(ghAttestationResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate)
            } else {
              null
            }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Cannot parse attestation $org/$repo/$name releaseTag committedDate string: "+ghAttestationResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate)
            null
        }

        // Each Attestation must have a public signing key file ".sign.pub"
        val attestation_sign_file = name+".sign.pub"
        val queryPubKey = RequestAttestationFileByName(org, repo, attestation_sign_file)
        val resultPubKey = try {
            graphQLGitHubInterface.queryApi(queryPubKey::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on attestation sign.pub file query $org/$repo/$attestation_sign_file :"+e)
            return null
        }

        if (resultPubKey == null || resultPubKey?.data == null) {
            // Not a valid Attestation if no .sign.pub file
            LOGGER.warn("WARNING: Attestation $org/$repo/$name is not valid as it does not have a valid associated sign.pub file: $attestation_sign_file")
            return null
        }

        val ghAttestation: GHAttestation? = try {
            val ghAtt = XmlMapper.mapper.readValue(ghAttestationResponse?.repository?.res_object?.text, GHAttestation::class.java)
            ghAtt.id = ghAttestationResponse?.repository?.res_object?.id ?: GitHubId("0")
            ghAtt.filename = name
            ghAtt.linkUrl = "https://github.com/"+org+"/"+repo+"/blob/"+ghAttestationResponse?.repository?.defaultBranchRef?.name+"/"+name
            ghAtt.linkSignUrl = "https://github.com/"+org+"/"+repo+"/blob/"+ghAttestationResponse?.repository?.defaultBranchRef?.name+"/"+attestation_sign_file
            ghAtt.committedDate = committedDate
            ghAtt
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping attestation $org/$repo/$name :"+e+" ghAttestationResponse: "+ghAttestationResponse)
            null
        }

        return ghAttestation
    }

    class RequestAttestationFileByName(val org: String, val repo: String, val name: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHAttestationResponseData> {

        fun withCursor(cursor: String?): RequestAttestationFileByName {
            return if (cursor != null) RequestAttestationFileByName(org, repo, name, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String get() = """
query {
      repository(owner: "${org}", name: "${repo}") {
        defaultBranchRef {
            name
            target {
              ... on Commit {
                history(first: 1, path: "${name}") {
                  nodes {
                    committedDate
                  }
                }
              }
            }
        }
        object(expression: "HEAD:${name}") {
            ... on Blob {
              id
              text
            }
        }
      }
      rateLimit {
        cost,
        remaining
      }
}
                    """

        override fun responseType(): KClass<GHAttestationResponseData> {
            return GHAttestationResponseData::class
        }
    }
}

