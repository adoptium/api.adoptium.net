package net.adoptium.marketplace.dataSources

import org.eclipse.microprofile.openapi.annotations.media.Schema

class ReleaseInfo {

    @Schema(example = "[8,9,10,11,12,13,14]", description = "The versions for which adopt have produced a ga release")
    val available_releases: Array<Int>

    @Schema(example = "[8,11]", description = "The LTS versions for which adopt have produced a ga release")
    val available_lts_releases: Array<Int>

    @Schema(example = "11", description = "The highest LTS version for which adopt have produced a ga release")
    val most_recent_lts: Int

    @Schema(example = "15", description = "The highest version (LTS or not) for which we have produced a build, this may be a version that has not yet produced a ga release")
    val most_recent_feature_version: Int

    constructor(
        availableReleases: Array<Int>,
        availableLtsReleases: Array<Int>,
        mostRecentLts: Int,
        mostRecentFeatureVersion: Int
    ) {
        this.available_releases = availableReleases
        this.available_lts_releases = availableLtsReleases
        this.most_recent_lts = mostRecentLts
        this.most_recent_feature_version = mostRecentFeatureVersion
    }
}
