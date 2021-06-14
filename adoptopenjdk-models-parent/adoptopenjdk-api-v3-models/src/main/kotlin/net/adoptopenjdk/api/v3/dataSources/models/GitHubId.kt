package net.adoptopenjdk.api.v3.dataSources.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class GitHubId {
    val id: String

    @JsonCreator
    constructor(
        @JsonProperty("id")
        gitHubId: String
    ) {
        this.id = gitHubId.split("\\.")[0]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GitHubId

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
