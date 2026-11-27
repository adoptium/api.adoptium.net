package net.adoptium.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class GHAsset @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("size") val size: Long,
    @JsonProperty("downloadUrl") val downloadUrl: String,
    @JsonProperty("downloadCount") val downloadCount: Long,
    @JsonProperty("updatedAt") val updatedAt: String,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("digest") val digest: String? = null
)

data class GHAssetNode @JsonCreator constructor(@JsonProperty("releaseAssets") val releaseAssets: GHAssets)
