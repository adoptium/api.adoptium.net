package net.adoptopenjdk.api.v3.dataSources.github

import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId

interface GitHubApi {
    suspend fun getRepository(owner: String, repoName: String): GHRepository
    suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary
    suspend fun getReleaseById(id: GitHubId): GHRelease?
}
