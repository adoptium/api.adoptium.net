package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.github.graphql.models.GitHubIdDeserializer

/*
    Models that encapsulate how GitHub represents its release data
 */

data class RateLimit @JsonCreator constructor(
    @JsonProperty("cost") val cost: Int,
    @JsonProperty("remaining") val remaining: Int
)

data class PageInfo @JsonCreator constructor(
    @JsonProperty("hasNextPage") val hasNextPage: Boolean,
    @JsonProperty("endCursor") val endCursor: String?
)

abstract class HasRateLimit(@JsonProperty("rateLimit") open val rateLimit: RateLimit)

class QueryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHRepository?,
    @JsonProperty("rateLimit") rateLimit: RateLimit
) : HasRateLimit(rateLimit)

class QuerySummaryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHRepositorySummary?,
    @JsonProperty("rateLimit") rateLimit: RateLimit
) : HasRateLimit(rateLimit)

class ReleaseQueryData @JsonCreator constructor(
    @JsonProperty("node") val assetNode: GHAssetNode?,
    @JsonProperty("rateLimit") rateLimit: RateLimit
) : HasRateLimit(rateLimit)

/* 
 Attestation file query example:
Query:
query RepoFiles($owner: String!, $name: String!, $expr: String!) {
  repository(owner: $owner, name: $name) {
    object(expression: HEAD:21/jdk_21_0_5_11_x64_linux_Adoptium.xml) {
            ... on Blob {
              id
              commitResourcePath
              text
            }
    }       
  }           
}
Response:
{
  "data": {
    "repository": {
      "object": {
        "id": "B_kwDONeZDk9oAKDk3ZDg0ZDI3MGEzNGY2Njc0OWU1ZTY2YjBhNDI5OTY4MjhiZGE0ZTE",
        "commitResourcePath": "/andrew-m-leonard/temurin-attestations/commit/97d84d270a34f66749e5e66b0a42996828bda4e1",
        "text": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bom .... </bom>\n"
      }
    }
  }
}
*/
class AttestationQueryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHAttestationRepository?,
    @JsonProperty("rateLimit") rateLimit: RateLimit
) : HasRateLimit(rateLimit)

class GHAttestationRepository @JsonCreator constructor(
    @JsonProperty("object") val object: GHAttestationObject?
)

class GHAttestationObject @JsonCreator constructor(
    @JsonProperty("id")
    @JsonDeserialize(using = GitHubIdDeserializer::class)
    val id: GitHubId,
    @JsonProperty("commitResourcePath") val commitResourcePath: String,
    @JsonProperty("text") val text: String
)

