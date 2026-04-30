package net.adoptium.api.v3.dataSources.github.graphql

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.github.GitHubApi
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubCdxaSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubCdxaClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxa
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.github.graphql.models.GHCdxaRepoSummaryData

@ApplicationScoped
open class GraphQLGitHubClient @Inject constructor(
    private val summaryClient: GraphQLGitHubSummaryClient,
    private val releaseClient: GraphQLGitHubReleaseClient,
    private val repositoryClientClient: GraphQLGitHubRepositoryClient,
    private val cdxaSummaryClient: GraphQLGitHubCdxaSummaryClient,
    private val cdxaClient: GraphQLGitHubCdxaClient
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

    override suspend fun getCdxaSummary(org: String, repo: String, directory: String): GHCdxaRepoSummaryData? {
        return cdxaSummaryClient.getCdxaSummary(org, repo, directory)
    }

    override suspend fun getCdxaByName(org: String, repo: String, name: String): GHCdxa? {
        return cdxaClient.getCdxaByName(org, repo, name)
    }
}
