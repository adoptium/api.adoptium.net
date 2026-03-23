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

data class GHCdxaRepoSummary @JsonCreator constructor(
    @JsonProperty("data") val data: GHCdxaRepoSummaryData
) {
}

data class GHCdxaRepoSummaryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: GHCdxaRepoSummaryRepository?,
    @JsonProperty("rateLimit") override val rateLimit: RateLimit
) : HasRateLimit(rateLimit) {
}

data class GHCdxaRepoSummaryRepository @JsonCreator constructor(
    @JsonProperty("defaultBranchRef") val defaultBranchRef: GHCdxaRepoSummaryDefaultBranchRef?,
    @JsonProperty("object") val att_object: GHCdxaRepoSummaryObject?
) {
}

data class GHCdxaRepoSummaryDefaultBranchRef @JsonCreator constructor(
    @JsonProperty("target")             var target: GHCdxaRepoSummaryDefaultBranchRefTarget?
) {
}

data class GHCdxaRepoSummaryDefaultBranchRefTarget @JsonCreator constructor(
    @JsonProperty("history")            var history: GHCdxaRepoSummaryDefaultBranchRefHistory?
) {
}

data class GHCdxaRepoSummaryDefaultBranchRefHistory @JsonCreator constructor(
    @JsonProperty("nodes")              var nodes: List<GHCdxaRepoSummaryDefaultBranchRefNode>?
) {
}

data class GHCdxaRepoSummaryDefaultBranchRefNode @JsonCreator constructor(
    @JsonProperty("committedDate")      var committedDate: String?
) {
}

data class GHCdxaRepoSummaryObject @JsonCreator constructor(
    @JsonProperty("entries")            var entries: List<GHCdxaRepoSummaryEntry>?
) { 
}

data class GHCdxaRepoSummaryEntry @JsonCreator constructor(
    @JsonProperty("name")   val name: String,
    @JsonProperty("type")   val type: String
) { 
}

