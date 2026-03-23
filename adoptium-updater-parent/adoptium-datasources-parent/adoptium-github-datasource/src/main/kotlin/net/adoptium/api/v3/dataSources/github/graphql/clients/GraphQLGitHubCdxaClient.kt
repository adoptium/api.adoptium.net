package net.adoptium.api.v3.dataSources.github.graphql.clients

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.XmlMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaResponseData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

import java.time.Instant

@ApplicationScoped
open class GraphQLGitHubCdxaClient @Inject constructor(
    private val graphQLGitHubInterface: GraphQLGitHubInterface
) {

    companion object {
        @JvmStatic
        private val LOGGER = LoggerFactory.getLogger(this::class.java)
    }

    open suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {

        LOGGER.debug("Getting cdxa $org/$repo/$name")

        val query = RequestCdxaFileByName(org, repo, name)
        val result = try {
            graphQLGitHubInterface.queryApi(query::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on cdxa query $org/$repo/$name :"+e)
            return null
        }

        val ghCdxaResponse: GHCdxaResponseData? = try {
          if (result == null || result?.data == null) {
            return null
          } else {
            result.data
          }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping cdxa query response $org/$repo/$name :"+e+" query result: "+result)
            return null
        }

        var committedDate: Instant? = try {
            if ( ghCdxaResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate != null ) {
              Instant.parse(ghCdxaResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate)
            } else {
              null
            }
        } catch (e: java.lang.Exception) {
            LOGGER.error("Cannot parse cdxa $org/$repo/$name releaseTag committedDate string: "+ghCdxaResponse?.repository?.defaultBranchRef?.target?.history?.nodes?.firstOrNull()?.committedDate)
            null
        }

        // Each Cdxa must have a public signing key file ".sig"
        val cdxa_sign_file = name+".sig"
        val queryPubKey = RequestCdxaFileByName(org, repo, cdxa_sign_file)
        val resultPubKey = try {
            graphQLGitHubInterface.queryApi(queryPubKey::withCursor, null)
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception on cdxa sig file query $org/$repo/$cdxa_sign_file :"+e)
            return null
        }

        if (resultPubKey == null || resultPubKey?.data == null) {
            // Not a valid Cdxa if no .sig file
            LOGGER.warn("WARNING: Cdxa $org/$repo/$name is not valid as it does not have a valid associated sig file: $cdxa_sign_file")
            return null
        }

        val ghCdxa: GHCdxa? = try {
            val ghAtt = XmlMapper.mapper.readValue(ghCdxaResponse?.repository?.res_object?.text, GHCdxa::class.java)
            ghAtt.id = ghCdxaResponse?.repository?.res_object?.id ?: GitHubId("0")
            ghAtt.filename = name
            ghAtt.linkUrl = "https://github.com/"+org+"/"+repo+"/blob/"+ghCdxaResponse?.repository?.defaultBranchRef?.name+"/"+name
            ghAtt.linkSigUrl = "https://github.com/"+org+"/"+repo+"/blob/"+ghCdxaResponse?.repository?.defaultBranchRef?.name+"/"+cdxa_sign_file
            ghAtt.committedDate = committedDate
            ghAtt
        } catch (e: java.lang.Exception) {
            LOGGER.error("Exception mapping cdxa $org/$repo/$name :"+e+" ghCdxaResponse: "+ghCdxaResponse)
            null
        }

        return ghCdxa
    }

    class RequestCdxaFileByName(val org: String, val repo: String, val name: String, override val variables: Any = mapOf<String, String>()) : GraphQLClientRequest<GHCdxaResponseData> {

        fun withCursor(cursor: String?): RequestCdxaFileByName {
            return if (cursor != null) RequestCdxaFileByName(org, repo, name, mapOf("cursorPointer" to cursor))
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

        override fun responseType(): KClass<GHCdxaResponseData> {
            return GHCdxaResponseData::class
        }
    }
}

