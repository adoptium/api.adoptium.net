package net.adoptopenjdk.api.v3.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

class DownloadStats {
    val date: ZonedDateTime
    val total_downloads: TotalStats
    val github_downloads: Map<Int, Long>
    val docker_pulls: Map<String, Long>

    @JsonCreator
    constructor(
        @JsonProperty("date")
        date: ZonedDateTime,
        @JsonProperty("total_downloads")
        total_downloads: TotalStats,
        @JsonProperty("github_downloads")
        github_downloads: Map<Int, Long>,
        @JsonProperty("docker_pulls")
        docker_pulls: Map<String, Long>
    ) {
        this.date = date
        this.total_downloads = total_downloads
        this.github_downloads = github_downloads
        this.docker_pulls = docker_pulls
    }
}

class TotalStats {
    val docker_pulls: Long
    val github_downloads: Long
    val total: Long

    @JsonCreator
    constructor(

        @JsonProperty("docker_pulls")
        docker_pulls: Long,
        @JsonProperty("github_downloads")
        github_downloads: Long,
        @JsonProperty("total")
        total: Long
    ) {
        this.docker_pulls = docker_pulls
        this.github_downloads = github_downloads
        this.total = total
    }
}
