package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/* Format example:
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
        "text": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bom ... </bom>\n"
      }
    }
  }
}
*/

data class GHAttestationResponse @JsonCreator constructor(
    @JsonProperty("data") val data: GHAttestationResponseData
) : HasRateLimit(rateLimit) {
}

data class GHAttestationResponseData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHAttestationResponseRepository
) {
}

data class GHAttestationResponseRepository @JsonCreator constructor(
    @JsonProperty("object") val repository: GHAttestationResponseObject
) {
}

data class GHAttestationResponseObject @JsonCreator constructor(
    @JsonProperty("id")
    @JsonDeserialize(using = GitHubIdDeserializer::class)
    val id: GitHubId,
    @JsonProperty("commitResourcePath")   val type: String,
    @JsonProperty("text")   val text: String
) { 
}

