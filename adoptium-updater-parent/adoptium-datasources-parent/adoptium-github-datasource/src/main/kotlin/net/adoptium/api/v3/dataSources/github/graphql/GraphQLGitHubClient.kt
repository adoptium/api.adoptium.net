package net.adoptium.api.v3.dataSources.github.graphql

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId

@ApplicationScoped
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

    override suspend fun getRepository(owner: String, repoName: String, filter: (updatedAt: String, isPrerelease: Boolean) -> Boolean): GHRepository {
        return repositoryClientClient.getRepository(owner, repoName, filter)
    }
}
