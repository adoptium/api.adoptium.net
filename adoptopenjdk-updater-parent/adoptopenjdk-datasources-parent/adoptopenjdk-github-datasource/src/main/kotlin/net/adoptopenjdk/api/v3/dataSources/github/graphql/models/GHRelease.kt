package net.adoptopenjdk.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId

data class GHReleases @JsonCreator constructor(
    @JsonProperty("nodes") val releases: List<GHRelease>,
    @JsonProperty("pageInfo") val pageInfo: PageInfo
)

data class GHReleaseResult @JsonCreator constructor(
    @JsonProperty("node") val release: GHRelease,
    @JsonProperty("rateLimit") override val rateLimit: RateLimit
) : HasRateLimit(rateLimit)

data class GHRelease @JsonCreator constructor(
    @JsonProperty("id")
    @JsonDeserialize(using = GitHubIdDeserializer::class)
    val id: GitHubId,
    @JsonProperty("name") val name: String,
    @JsonProperty("isPrerelease") val isPrerelease: Boolean,
    @JsonProperty("publishedAt") val publishedAt: String,
    @JsonProperty("updatedAt") val updatedAt: String,
    @JsonProperty("releaseAssets") val releaseAssets: GHAssets,
    @JsonProperty("resourcePath") val resourcePath: String,
    @JsonProperty("url") val url: String
)
