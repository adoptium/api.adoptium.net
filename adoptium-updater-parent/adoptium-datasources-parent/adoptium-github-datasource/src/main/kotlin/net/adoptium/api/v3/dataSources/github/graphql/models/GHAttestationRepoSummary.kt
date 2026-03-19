package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/* Format example:
Query:
query RepoFiles($owner: String!, $name: String!, $dir: String!) {
  repository(owner: $owner, name: $name) {
    defaultBranchRef {
      target {
        ... on Commit {
          history(first: 1, path: ./$dir) {
            nodes {
              committedDate
            }
          }
        }
      }
    }
    object(expression: "HEAD:$dir") {
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
Response:
{
  "data": {
    "repository": {
      "defaultBranchRef": {
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
        "entries": [
          {
            "name": "jdk_21_0_5_11_x64_linux_Adoptium.xml",
            "type": "blob"
          },
          {
            "name": "jdk_21_0_5_11_x64_linux_Adoptium.xml.sign.pub",
            "type": "blob"
          }
        ]
      }
    },
    "rateLimit": {
      "cost": 1,
      "remaining": 4974
    }
  }
}

*/

data class GHAttestationRepoSummary @JsonCreator constructor(
    @JsonProperty("data") val data: GHAttestationRepoSummaryData
) {
}

data class GHAttestationRepoSummaryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHAttestationRepoSummaryRepository?,
    @JsonProperty("rateLimit") override val rateLimit: RateLimit
) : HasRateLimit(rateLimit) {
}

data class GHAttestationRepoSummaryRepository @JsonCreator constructor(
    @JsonProperty("defaultBranchRef") val defaultBranchRef: GHAttestationRepoSummaryDefaultBranchRef?,
    @JsonProperty("object") val att_object: GHAttestationRepoSummaryObject?
) {
}

data class GHAttestationRepoSummaryDefaultBranchRef @JsonCreator constructor(
    @JsonProperty("target")             var target: GHAttestationRepoSummaryDefaultBranchRefTarget?
) {
}

data class GHAttestationRepoSummaryDefaultBranchRefTarget @JsonCreator constructor(
    @JsonProperty("history")            var history: GHAttestationRepoSummaryDefaultBranchRefHistory?
) {
}

data class GHAttestationRepoSummaryDefaultBranchRefHistory @JsonCreator constructor(
    @JsonProperty("nodes")              var nodes: List<GHAttestationRepoSummaryDefaultBranchRefNode>?
) {
}

data class GHAttestationRepoSummaryDefaultBranchRefNode @JsonCreator constructor(
    @JsonProperty("committedDate")      var committedDate: String?
) {
}

data class GHAttestationRepoSummaryObject @JsonCreator constructor(
    @JsonProperty("entries")            var entries: List<GHAttestationRepoSummaryEntry>?
) { 
}

data class GHAttestationRepoSummaryEntry @JsonCreator constructor(
    @JsonProperty("name")   val name: String,
    @JsonProperty("type")   val type: String
) { 
}

