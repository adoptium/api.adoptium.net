package net.adoptium.api

import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubInterface
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseRequest
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLRequest
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.QueryData
import net.adoptium.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GraphQLGitHubReleaseClientTest : BaseTest() {
    companion object {
        val source = GHAssets(
            listOf(
                GHAsset(
                    "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json",
                    1L,
                    "1",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            ),
            PageInfo(false, ""),
            2
        )
        val response = GHRelease(
            id = GitHubId("1"),
            name = "jdk9u-2018-09-27-08-50",
            isPrerelease = true,
            publishedAt = "2013-02-27T19:35:32Z",
            updatedAt = "2013-02-27T19:35:32Z",
            releaseAssets = source,
            resourcePath = "8",
            url = "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk9u-2018-09-27-08-50/OpenJDK9U-jre_aarch64_linux_hotspot_2018-09-27-08-50.tar.gz"
        )

        val repo = GHRepository(GHReleases(listOf(response), PageInfo(false, null)))
    }

    @Test
    fun `GraphQLGitHubReleaseClient client returns correct release`() {
        runBlocking {
            val graphQLRequest = object : GraphQLRequest {
                override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                    val builder = mockk<GraphQLClientResponse<F>>()

                    assert(query.query.contains("a-github-id"))
                    every { builder.data } returns GHReleaseResult(response, RateLimit(0, 5000)) as F
                    every { builder.errors } returns null
                    return builder
                }
            }

            val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
            val graphQLGitHubReleaseRequest = GraphQLGitHubReleaseRequest(graphQLGitHubInterface)

            val client = GraphQLGitHubReleaseClient(graphQLGitHubInterface, graphQLGitHubReleaseRequest)
            val release = client.getReleaseById(GitHubId("a-github-id"))

            assertEquals(response, release)
        }
    }

    @Test
    fun `GraphQLGitHubRepositoryClient client returns correct repository`() {
        runBlocking {
            val graphQLRequest = object : GraphQLRequest {
                override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                    val builder = mockk<GraphQLClientResponse<F>>()

                    assert(query.query.contains("a-repo-name"))

                    every { builder.data } returns QueryData(repo, RateLimit(0, 5000)) as F
                    every { builder.errors } returns null
                    return builder
                }
            };

            val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
            val graphQLGitHubReleaseRequest = GraphQLGitHubReleaseRequest(graphQLGitHubInterface)

            val client = GraphQLGitHubRepositoryClient(graphQLGitHubInterface, graphQLGitHubReleaseRequest)

            val repo = client.getRepository(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name")

            assertEquals(Companion.repo, repo)
        }
    }

    @Test
    fun `GraphQLGitHubSummaryClient client returns correct repository`() {
        runBlocking {
            val summary = QuerySummaryData(
                GHRepositorySummary(
                    GHReleasesSummary(
                        listOf(
                            GHReleaseSummary(
                                GitHubId("foo"),
                                "a",
                                "b",
                                "c",
                                GHAssetsSummary(0)
                            )
                        ),
                        PageInfo(false, null)
                    )
                ),
                RateLimit(0, 5000)
            )

            val graphQLRequest = object : GraphQLRequest {
                override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                    val builder = mockk<GraphQLClientResponse<F>>()

                    assert(query.query.contains("a-repo-name"))

                    every { builder.data } returns summary as F
                    every { builder.errors } returns null
                    return builder
                }
            }
            val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())

            val client = GraphQLGitHubSummaryClient(graphQLGitHubInterface)

            val repo = client.getRepositorySummary(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name")

            assertEquals(summary.repository, repo)
        }
    }

    @Test
    fun `requests second page`() {
        runBlocking {

            val graphQLRequest = object : GraphQLRequest {
                override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                    val builder = mockk<GraphQLClientResponse<F>>()

                    assert(query.query.contains("a-repo-name"))

                    val pageInfo = if ((query.variables as Map<String, String>)["cursorPointer"] != null) {
                        PageInfo(false, null)
                    } else {
                        PageInfo(true, "next-page-id")
                    }

                    val repo = GHRepository(GHReleases(listOf(response), pageInfo))

                    every { builder.data } returns QueryData(repo, RateLimit(0, 5000)) as F
                    every { builder.errors } returns null
                    return builder
                }
            }


            val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
            val graphQLGitHubReleaseRequest = GraphQLGitHubReleaseRequest(graphQLGitHubInterface)

            val client = GraphQLGitHubRepositoryClient(graphQLGitHubInterface, graphQLGitHubReleaseRequest)

            val repo = client.getRepository(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name")

            assertEquals(2, repo.releases.releases.size)
        }
    }
}
