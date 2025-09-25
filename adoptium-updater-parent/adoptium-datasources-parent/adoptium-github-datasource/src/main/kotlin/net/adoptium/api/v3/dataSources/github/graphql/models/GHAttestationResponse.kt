package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptium.api.v3.dataSources.models.GitHubId

/* Format example:
Query:
query RepoFiles($owner: String!, $name: String!, $expr: String!) {
  repository(owner: $owner, name: $name) {
    defaultBranchRef {
      name
      target {
              ... on Commit {
                history(first: 1, path: 21/jdk_21_0_5_11_x64_linux_Adoptium.xml) {
                  nodes {
                    committedDate
                  }
                }
              }
      }
    },
    object(expression: HEAD:21/jdk_21_0_5_11_x64_linux_Adoptium.xml) {
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
Response:
{
  "data": {
    "repository": {
      "defaultBranchRef": {
        "name": "main",
        "target": {
                  "history": {
                    "nodes": [
                      {
                        "committedDate": "2025-09-24T13:58:12Z"
                      }
                    ]
                  }
        }
      },
      "object": {
        "id": "B_kwDONeZDk9oAKDk3ZDg0ZDI3MGEzNGY2Njc0OWU1ZTY2YjBhNDI5OTY4MjhiZGE0ZTE",
        "text": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bom ... </bom>\n"
      }
    },
    "rateLimit": {
      "cost":1,
      "remaining":4586
    }
  }
}
*/

data class GHAttestationResponse @JsonCreator constructor(
    @JsonProperty("data") val data: GHAttestationResponseData
) {
}

data class GHAttestationResponseData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHAttestationResponseRepository,
    @JsonProperty("rateLimit") override val rateLimit: RateLimit
) : HasRateLimit(rateLimit) {
}

data class GHAttestationResponseRepository @JsonCreator constructor(
    @JsonProperty("defaultBranchRef") val defaultBranchRef: GHAttestationResponseDefaultBranchRef,
    @JsonProperty("object") val res_object: GHAttestationResponseObject
) {
}

data class GHAttestationResponseDefaultBranchRef @JsonCreator constructor(
    @JsonProperty("name")   val name: String,
    @JsonProperty("target") val target: GHAttestationResponseDefaultBranchRefTarget?
) {
}

data class GHAttestationResponseDefaultBranchRefTarget @JsonCreator constructor(
    @JsonProperty("history")            var history: GHAttestationResponseDefaultBranchRefHistory?
) {
}

data class GHAttestationResponseDefaultBranchRefHistory @JsonCreator constructor(
    @JsonProperty("nodes")              var nodes: List<GHAttestationResponseDefaultBranchRefNode>?
) {
}

data class GHAttestationResponseDefaultBranchRefNode @JsonCreator constructor(
    @JsonProperty("committedDate")      var committedDate: String?
) {
}

data class GHAttestationResponseObject @JsonCreator constructor(
    @JsonProperty("id")
    @JsonDeserialize(using = GitHubIdDeserializer::class)
    val id: GitHubId,
    @JsonProperty("text")   val text: String
) { 
}

