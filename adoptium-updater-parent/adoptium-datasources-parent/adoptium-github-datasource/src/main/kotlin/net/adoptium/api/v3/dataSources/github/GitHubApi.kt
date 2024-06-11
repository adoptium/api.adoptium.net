package net.adoptium.api.v3.dataSources.github

import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId

interface GitHubApi {
    suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository
    suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary
    suspend fun getReleaseById(id: GitHubId): GHRelease?
}
