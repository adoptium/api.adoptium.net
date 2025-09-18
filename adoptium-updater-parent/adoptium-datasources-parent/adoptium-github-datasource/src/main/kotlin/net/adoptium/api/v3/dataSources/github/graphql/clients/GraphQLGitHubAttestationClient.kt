package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.XmlMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestationResponse
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAttestation
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

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
        val result = graphQLGitHubInterface.queryApi(query::withCursor, null)

        val ghAttestationResponse: GHAttestationResponse? = if (result?.data == null) {
            return null
        } else {
            result.data
        }

        // Each Attestation must have a public signing key file ".sign.pub"
        val attestation_sign_file = name+".sign.pub"
        val queryPubKey = RequestAttestationFileByName(org, repo, attestation_sign_file)
        val resultPubKey = graphQLGitHubInterface.queryApi(query::withCursor, null)
        if (resultPubKey?.data == null) {
            // Not a valid Attestation if no .sign.pub file
            return null
        }

        val ghAttestation = XmlMapper.mapper.readValue(ghAttestationResponse?.data?.repository?.repository?.text, GHAttestation::class.java)
        ghAttestation.id = ghAttestationResponse?.data?.repository?.repository?.id ?: GitHubId("0")
        ghAttestation.commitResourcePath = ghAttestationResponse?.data?.repository?.repository?.commitResourcePath ?: ""
        ghAttestation.filename = name

        return ghAttestation
    }

    class RequestAttestationFileByName(val org: String, val repo: String, val name: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHAttestationResponse> {

        fun withCursor(cursor: String?): RequestAttestationFileByName {
            return if (cursor != null) RequestAttestationFileByName(org, repo, name, mapOf("cursorPointer" to cursor))
            else this
        }

        override val query: String
            get() = """query {
                          repository(owner: "${org}", name: "${repo}") {
        object(expression: "HEAD:${name}") {
            ... on Blob {
              id
              commitResourcePath
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

        override fun responseType(): KClass<GHAttestationResponse> {
            return GHAttestationResponse::class
        }
    }
}

