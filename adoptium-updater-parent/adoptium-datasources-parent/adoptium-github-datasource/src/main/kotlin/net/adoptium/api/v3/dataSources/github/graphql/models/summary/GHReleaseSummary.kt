package net.adoptium.api.v3.dataSources.github.graphql.models.summary

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.graphql.models.GitHubIdDeserializer
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class GHReleasesSummary @JsonCreator constructor(
    @JsonProperty("nodes") val releases: List<GHReleaseSummary>,
    @JsonProperty("pageInfo") val pageInfo: PageInfo
) {
    fun getIds(): List<GitHubId> {
        return releases.map { it.id }
    }
}

data class GHReleaseSummary @JsonCreator constructor(
    @JsonProperty("id")
    @JsonDeserialize(using = GitHubIdDeserializer::class)
    val id: GitHubId,
    @JsonProperty("publishedAt") val publishedAt: String,
    @JsonProperty("updatedAt") val updatedAt: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("releaseAssets") val releaseAssets: GHAssetsSummary
) {

    fun getUpdatedTime(): ZonedDateTime {
        return parseDate(updatedAt)
    }

    fun getPublishedTime(): ZonedDateTime {
        return parseDate(publishedAt)
    }

    private fun parseDate(date: String): ZonedDateTime {
        return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(date))
            .atZone(TimeSource.ZONE)
    }
}

data class GHAssetsSummary @JsonCreator constructor(
    @JsonProperty("totalCount") val totalCount: Int
)
