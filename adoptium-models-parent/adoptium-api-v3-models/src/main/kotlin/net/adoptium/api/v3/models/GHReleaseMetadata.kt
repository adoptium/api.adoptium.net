package net.adoptium.api.v3.models

import net.adoptium.api.v3.dataSources.models.GitHubId

data class GHReleaseMetadata(
    val totalBinaryCount: Int,
    val gitHubId: GitHubId
)
