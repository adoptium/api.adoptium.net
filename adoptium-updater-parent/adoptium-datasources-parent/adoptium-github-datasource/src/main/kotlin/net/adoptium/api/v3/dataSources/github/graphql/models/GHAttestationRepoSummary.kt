package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

/* Format example:
Query:
query RepoFiles($owner: String!, $name: String!, $expr: String!) {
  repository(owner: $owner, name: $name) {
    object(expression: $expr) {
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
}
Response:
{
  "data": {
    "repository": {
      "object": {
        "entries": [
          {
            "name":".github",
            "type":"tree",
            "object": {
              "entries": [
                {
                  "name":"workflows",
                  "type":"tree"
                }
              ]
            }
          },
          {
            "name":"21",
            "type":"tree",
            "object": {
              "entries": [
                {
                  "name":"jdk_21_0_5_11_x64_linux_Adoptium.xml",
                  "type":"blob",
                },
                {
                  "name":"jdk_21_0_5_11_x64_linux_Adoptium.xml.sign.pub",
                  "type":"blob",
                }
              ]
            }
          },
          {
            "name":"README.md",
            "type":"blob",
            "object":{}
          }
        ]
      }
    },
    "rateLimit": {
      "cost":1,
      "remaining":4586
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
    @JsonProperty("object") val att_object: GHAttestationRepoSummaryObject?
) {
}

data class GHAttestationRepoSummaryObject @JsonCreator constructor(
    @JsonProperty("entries")            var entries: List<GHAttestationRepoSummaryEntry>?
) { 
}

data class GHAttestationRepoSummaryEntry @JsonCreator constructor(
    @JsonProperty("name")   val name: String,
    @JsonProperty("type")   val type: String,
    @JsonProperty("object") val att_object: GHAttestationRepoSummaryObject?
) { 
}

