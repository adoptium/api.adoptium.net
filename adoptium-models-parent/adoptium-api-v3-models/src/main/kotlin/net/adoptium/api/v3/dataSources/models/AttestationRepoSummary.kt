package net.adoptium.api.v3.dataSources.models

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
                  object {
                    ... on Blob {
                      commitResourcePath
                    }
                  }
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
            "name": ".github",
            "type": "tree",
            "object": {
              "entries": [
                {
                  "name": "workflows",
                  "type": "tree",
                  "object": {}
                }
              ]
            }
          },
          {
            "name": "21",
            "type": "tree",
            "object": {
              "entries": [
                {
                  "name": "jdk_21_0_5_11_x64_linux_Adoptium.xml",
                  "type": "blob",
                  "object": {
                    "commitResourcePath": "/andrew-m-leonard/temurin-attestations/commit/97d84d270a34f66749e5e66b0a42996828bda4e1"
                  }
                },
                {
                  "name": "jdk_21_0_5_11_x64_linux_Adoptium.xml.sign.pub",
                  "type": "blob",
                  "object": {
                    "commitResourcePath": "/andrew-m-leonard/temurin-attestations/commit/d65966533a06d90147e8f4ad963dcf7bb52e645e"
                  }
                }
              ]
            }
          },
          {
            "name": "README.md",
            "type": "blob",
            "object": {}
          }
        ]
      }
    }
  }
}

*/

data class AttestationRepoSummary @JsonCreator constructor(
    @JsonProperty("data") val data: AttestationRepoSummaryData?
) {
}

data class AttestationRepoSummaryData @JsonCreator constructor(
    @JsonProperty("repository") val repository: AttestationRepoSummaryRepository?
) {
}

data class AttestationRepoSummaryRepository @JsonCreator constructor(
    @JsonProperty("object") val att_object: AttestationRepoSummaryObject?
) {
}

data class AttestationRepoSummaryObject @JsonCreator constructor(
    @JsonProperty("commitResourcePath") val commitResourcePath: String?,
    @JsonProperty("entries")            val entries: List<AttestationRepoSummaryEntry>?
) { 
}

data class AttestationRepoSummaryEntry @JsonCreator constructor(
    @JsonProperty("name")   val name: String,
    @JsonProperty("type")   val type: String,
    @JsonProperty("object") val att_object: AttestationRepoSummaryObject?
) { 
}

