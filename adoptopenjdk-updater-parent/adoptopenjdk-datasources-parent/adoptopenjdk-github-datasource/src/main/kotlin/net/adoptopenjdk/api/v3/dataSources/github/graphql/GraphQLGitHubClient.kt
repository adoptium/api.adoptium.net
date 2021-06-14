package net.adoptopenjdk.api.v3.dataSources.github.graphql

import net.adoptopenjdk.api.v3.dataSources.github.GitHubApi
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class GraphQLGitHubClient @Inject constructor(
    private val summaryClient: GraphQLGitHubSummaryClient,
    private val releaseClient: GraphQLGitHubReleaseClient,
    private val repositoryClientClient: GraphQLGitHubRepositoryClient
) : GitHubApi {

    override suspend fun getRepositorySummary(owner: String, repoName: String): GHRepositorySummary {
        return summaryClient.getRepositorySummary(owner, repoName)
    }

    override suspend fun getReleaseById(id: GitHubId): GHRelease? {
        return releaseClient.getReleaseById(id)
    }

    override suspend fun getRepository(owner: String, repoName: String): GHRepository {
        return repositoryClientClient.getRepository(owner, repoName)
    }
}
