package net.adoptopenjdk.api.v3.models

import org.eclipse.microprofile.openapi.annotations.media.Schema

class ReleaseInfo {

    @Schema(example = "[8,9,10,11,12,13,14]", description = "The versions for which adopt have produced a ga release")
    val available_releases: Array<Int>

    @Schema(example = "[8,11]", description = "The LTS versions for which adopt have produced a ga release")
    val available_lts_releases: Array<Int>

    @Schema(example = "11", description = "The highest LTS version for which adopt have produced a ga release")
    val most_recent_lts: Int

    @Schema(example = "13", description = "The highest version (LTS or not) for which adopt have produced a ga release")
    val most_recent_feature_release: Int

    @Schema(example = "15", description = "The highest version (LTS or not) for which we have produced a build, this may be a version that has not yet produced a ga release")
    val most_recent_feature_version: Int

    @Schema(example = "15", description = "The version that is currently in development at openjdk")
    val tip_version: Int

    constructor(
        available_releases: Array<Int>,
        available_lts_releases: Array<Int>,
        most_recent_lts: Int,
        most_recent_feature_release: Int,
        most_recent_feature_version: Int,
        tip_version: Int
    ) {
        this.available_releases = available_releases
        this.available_lts_releases = available_lts_releases
        this.most_recent_lts = most_recent_lts
        this.most_recent_feature_release = most_recent_feature_release
        this.most_recent_feature_version = most_recent_feature_version
        this.tip_version = tip_version
    }
}
