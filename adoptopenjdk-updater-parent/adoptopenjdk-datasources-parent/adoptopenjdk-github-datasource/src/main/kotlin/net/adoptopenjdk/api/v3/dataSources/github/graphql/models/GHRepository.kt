package net.adoptopenjdk.api.v3.dataSources.github.graphql.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class GHRepository @JsonCreator constructor(
    @JsonProperty("releases") val releases: GHReleases
) {
    fun getReleases(): List<GHRelease> {
        return releases.releases
    }
}
