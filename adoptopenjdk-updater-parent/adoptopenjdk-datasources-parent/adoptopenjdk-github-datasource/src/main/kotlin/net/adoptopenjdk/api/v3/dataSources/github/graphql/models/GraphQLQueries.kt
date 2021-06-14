package net.adoptopenjdk.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary

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
