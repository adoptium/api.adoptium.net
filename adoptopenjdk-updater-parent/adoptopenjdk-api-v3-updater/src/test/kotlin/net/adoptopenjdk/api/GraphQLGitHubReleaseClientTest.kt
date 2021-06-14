package net.adoptopenjdk.api

import io.aexp.nodes.graphql.GraphQLRequestEntity
import io.aexp.nodes.graphql.GraphQLResponseEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.AdoptRepositoryImpl
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.clients.GraphQLRequest
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.QueryData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.RateLimit
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
            PageInfo(false, "")
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
            val client = GraphQLGitHubReleaseClient(
                object : GraphQLRequest {
                    override fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F> {
                        val builder = mockk<GraphQLResponseEntity<F>>()

                        assert(query.toString().contains("a-github-id"))
                        every { builder.response } returns GHReleaseResult(response, RateLimit(0, 5000)) as F
                        return builder
                    }
                },
                mockkHttpClient()
            )

            val release = client.getReleaseById(GitHubId("a-github-id"))

            assertEquals(response, release)
        }
    }

    @Test
    fun `GraphQLGitHubRepositoryClient client returns correct repository`() {
        runBlocking {
            val client = GraphQLGitHubRepositoryClient(
                object : GraphQLRequest {
                    override fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F> {
                        val builder = mockk<GraphQLResponseEntity<F>>()

                        assert(query.toString().contains("a-repo-name"))

                        every { builder.response } returns QueryData(repo, RateLimit(0, 5000)) as F
                        every { builder.errors } returns null
                        return builder
                    }
                },
                mockkHttpClient()
            )

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
                        listOf(GHReleaseSummary(GitHubId("foo"), "a", "b")),
                        PageInfo(false, null)
                    )
                ),
                RateLimit(0, 5000)
            )

            val client = GraphQLGitHubSummaryClient(
                object : GraphQLRequest {
                    override fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F> {
                        val builder = mockk<GraphQLResponseEntity<F>>()

                        assert(query.toString().contains("a-repo-name"))

                        every { builder.response } returns summary as F
                        every { builder.errors } returns null
                        return builder
                    }
                },
                mockkHttpClient()
            )

            val repo = client.getRepositorySummary(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name")

            assertEquals(summary.repository, repo)
        }
    }

    @Test
    fun `requests second page`() {
        runBlocking {
            val client = GraphQLGitHubRepositoryClient(
                object : GraphQLRequest {
                    override fun <F> query(query: GraphQLRequestEntity, clazz: Class<F>): GraphQLResponseEntity<F> {
                        val builder = mockk<GraphQLResponseEntity<F>>()

                        assert(query.toString().contains("a-repo-name"))

                        val pageInfo = if (query.variables["cursorPointer"] != null) {
                            PageInfo(false, null)
                        } else {
                            PageInfo(true, "next-page-id")
                        }

                        val repo = GHRepository(GHReleases(listOf(response), pageInfo))

                        every { builder.response } returns QueryData(repo, RateLimit(0, 5000)) as F
                        every { builder.errors } returns null
                        return builder
                    }
                },
                mockkHttpClient()
            )

            val repo = client.getRepository(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name")

            assertEquals(2, repo.releases.releases.size)
        }
    }
}
