package net.adoptium.api

import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.ReleaseIncludeFilter
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.AdoptRepository
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.config.Ecosystem
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.GraphQLGitHubClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubInterface
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubReleaseRequest
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubRepositoryClient.GetQueryData
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubCdxaSummaryClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLGitHubCdxaClient
import net.adoptium.api.v3.dataSources.github.graphql.clients.GraphQLRequest
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssetNode
import net.adoptium.api.v3.dataSources.github.graphql.models.ReleaseQueryData
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.HasRateLimit
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.QueryData
import net.adoptium.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.mapping.ReleaseMapper.Companion.parseDate
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime
import java.util.stream.Stream

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

                    assert(query.query?.contains("a-github-id") == true)
                    assertAssetIdentityFields(query.query!!)
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

                    assert(query.query?.contains("a-repo-name") == true)
                    assertAssetIdentityFields(query.query!!)

                    every { builder.data } returns QueryData(repo, RateLimit(0, 5000)) as F
                    every { builder.errors } returns null
                    return builder
                }
            }

            val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
            val graphQLGitHubReleaseRequest = GraphQLGitHubReleaseRequest(graphQLGitHubInterface)

            val client = GraphQLGitHubRepositoryClient(graphQLGitHubInterface, graphQLGitHubReleaseRequest)

            val repo = client.getRepository(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name") { _, _ -> true }

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

                    assert(query.query?.contains("a-repo-name") == true)

                    assertTrue(query.query!!.contains("isPrerelease"))
                    assertTrue(query.query!!.contains("url,"))

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

                    assert(query.query?.contains("a-repo-name") == true)
                    assertAssetIdentityFields(query.query!!)

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

            val repo = client.getRepository(AdoptRepositoryImpl.ADOPT_ORG, "a-repo-name") { _, _ -> true }

            assertEquals(2, repo.releases.releases.size)
        }
    }

    private fun assertAssetIdentityFields(query: String) {
        assertTrue(Regex("releaseAssets[^{}]*\\{[^{}]*nodes\\s*\\{\\s*id,\\s*digest,", RegexOption.DOT_MATCHES_ALL).containsMatchIn(query))
    }

    @Test
    fun `subsequent asset pages request and preserve identity and digest`() = runBlocking {
        val first = source.assets.first().copy(id = "asset-one", digest = "sha256:" + "ab".repeat(32))
        val second = source.assets.last().copy(id = "asset-two", digest = "sha256:" + "cd".repeat(32))
        var requests = 0
        val request = object : GraphQLRequest {
            override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                requests++
                assertAssetIdentityFields(query.query!!)
                val data = if (requests == 1) {
                    GHReleaseResult(response.copy(releaseAssets = GHAssets(listOf(first), PageInfo(true, "next-assets"), 2)), RateLimit(0, 5000))
                } else {
                    assertEquals("next-assets", (query.variables as Map<*, *>)["cursorPointer"])
                    ReleaseQueryData(GHAssetNode(GHAssets(listOf(second), PageInfo(false, null), 2)), RateLimit(0, 5000))
                }
                return mockk<GraphQLClientResponse<F>>().also {
                    every { it.data } returns data as F
                    every { it.errors } returns null
                }
            }
        }
        val api = GraphQLGitHubInterface(request, mockkHttpClient())
        val client = GraphQLGitHubReleaseClient(api, GraphQLGitHubReleaseRequest(api))
        assertEquals(listOf(first, second), client.getReleaseById(response.id)!!.releaseAssets.assets)
        assertEquals(2, requests)
    }

    @Test
    fun `asset identity and digest are optional for older responses`() {
        val json = """{"name":"sidecar.json","size":1,"downloadUrl":"url","downloadCount":2,"updatedAt":"date"}"""
        val legacy = UpdaterJsonMapper.mapper.readValue(json, GHAsset::class.java)
        assertNull(legacy.id)
        assertNull(legacy.digest)
        val current = UpdaterJsonMapper.mapper.readValue(json.dropLast(1) + """, "id":"immutable", "digest":"sha256:digest"}""", GHAsset::class.java)
        assertEquals("immutable", current.id)
        assertEquals("sha256:digest", current.digest)
    }

    @TestFactory
    fun filtersReleases(): Stream<DynamicTest> {
        val releaseDate = parseDate("2013-02-27T19:35:32Z")
        return listOf(
            Triple(
                "prereleases more than 90 days old are ignored",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(91),
                    ReleaseFilterType.ALL,
                    excludedVendors = setOf()
                ),
                0
            ),
            Triple(
                "prereleases less than 90 days old are not ignored",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(89),
                    ReleaseFilterType.ALL,
                    excludedVendors = setOf()
                ),
                1
            ),
            Triple(
                "release type filter is applied",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(89),
                    ReleaseFilterType.RELEASES_ONLY,
                    excludedVendors = setOf()
                ),
                0
            ),
            Triple(
                "date filter is applied to non-excluded vendor",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(91),
                    ReleaseFilterType.RELEASES_ONLY,
                    excludedVendors = setOf()
                ),
                0
            ),
            Triple(
                "Excluded vendor more than 90 days is ignored",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(91),
                    ReleaseFilterType.ALL,
                    excludedVendors = setOf(Vendor.getDefault())
                ),
                0
            ),
            Triple(
                "Excluded vendor less than 90 days is ignored",
                ReleaseIncludeFilter(
                    releaseDate.plusDays(89),
                    ReleaseFilterType.ALL,
                    excludedVendors = setOf(Vendor.getDefault())
                ),
                0
            ),
        )
            .map {
                return@map DynamicTest.dynamicTest(it.first) {
                    runBlocking {
                        val repository = setupFilterTest()
                        val repo = repository.getRelease(8, it.second)
                        assertEquals(it.third, repo.releases.getReleases().count())
                    }
                }
            }
            .stream()
    }

    @ParameterizedTest
    @ValueSource(strings = ["null-response", "missing-data", "missing-repository", "not-found", "missing-cursor", "repeated-cursor"])
    fun `incomplete summary pagination never returns a prefix`(failure: String) = runBlocking {
        var calls = 0
        val transport = object : GraphQLGitHubInterface(mockk(), mockk()) {
            override suspend fun <F : HasRateLimit> queryApi(
                requestEntityBuilder: (String?) -> GraphQLClientRequest<F>, cursor: String?
            ): GraphQLClientResponse<F>? {
                check(++calls <= 3) { "Pagination failed to stop" }
                if (cursor != null && failure == "null-response") return null
                val page = if (failure == "missing-cursor") PageInfo(true, null) else PageInfo(true, "next")
                val data = if (cursor == null || failure == "repeated-cursor") QuerySummaryData(
                    GHRepositorySummary(GHReleasesSummary(
                        listOf(GHReleaseSummary(GitHubId("kept"), "2013-02-27T19:35:32Z", "2013-02-27T19:35:32Z", "kept", GHAssetsSummary(1))), page)),
                    RateLimit(0, 5000)
                ) else if (failure == "missing-repository") QuerySummaryData(null, RateLimit(0, 5000)) else null
                return mockk<GraphQLClientResponse<F>>().also { result ->
                    every { result.data } returns (data as F?)
                    if (cursor != null && failure == "not-found") {
                        every { result.errors } returns listOf(mockk { every { message } returns "Could not resolve to a Repository" })
                    } else every { result.errors } returns null
                }
            }
        }
        val summaryClient = GraphQLGitHubSummaryClient(transport)
        val repository = mockk<AdoptRepository>()
        coEvery { repository.getSummary(any()) } coAnswers { summaryClient.getRepositorySummary("adoptium", "test") }
        val previous = AdoptReposTestDataGenerator.generate()
        // Abort before pruning: the caller must not receive a partial upstream ID list.
        assertThrows(Exception::class.java) {
            runBlocking { AdoptReposBuilder(repository, mockk()).incrementalUpdate(emptySet(), previous) { null } }
        }
        assertEquals(if (failure == "missing-cursor") 1 else 2, calls)
    }

    @Test
    fun `explicit absent repository is empty only on the initial request`() = runBlocking {
        val request = object : GraphQLRequest {
            override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> =
                mockk<GraphQLClientResponse<F>>().also { result ->
                    every { result.data } returns null
                    every { result.errors } returns listOf(mockk { every { message } returns "Could not resolve to a Repository" })
                }
        }
        val transport = GraphQLGitHubInterface(request, mockk())
        assertEquals(emptyList<GHReleaseSummary>(), GraphQLGitHubSummaryClient(transport).getRepositorySummary("adoptium", "absent").releases.releases)
        assertEquals(emptyList<GHRelease>(), GraphQLGitHubRepositoryClient(transport, GraphQLGitHubReleaseRequest(transport))
            .getRepository("adoptium", "absent") { _, _ -> true }.releases.releases)
    }

    private fun setupFilterTest(): AdoptRepositoryImpl {
        val graphQLRequest = object : GraphQLRequest {
            override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                val builder = mockk<GraphQLClientResponse<F>>()

                val match = if (Ecosystem.CURRENT == Ecosystem.adoptopenjdk) {
                    (query as GetQueryData).owner.lowercase() == "adoptopenjdk" &&
                        (query as GetQueryData).repoName == "openjdk8-binaries"
                } else {
                    (query as GetQueryData).owner.lowercase() == "adoptium" &&
                        (query as GetQueryData).repoName == "temurin8-binaries"
                }

                if (match) {
                    every { builder.data } returns QueryData(repo, RateLimit(0, 5000)) as F
                } else {
                    every { builder.data } returns QueryData(GHRepository(GHReleases(listOf(), PageInfo(false, null))), RateLimit(0, 5000)) as F
                }

                every { builder.errors } returns null
                return builder
            }
        }

        val graphQLGitHubInterface = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
        val graphQLGitHubReleaseRequest = GraphQLGitHubReleaseRequest(graphQLGitHubInterface)

        val client = GraphQLGitHubRepositoryClient(graphQLGitHubInterface, graphQLGitHubReleaseRequest)


        val htmlClient = mockk<GitHubHtmlClient>()
        coEvery { htmlClient.getUrl(any()) }.returns(null)

        val adoptReleaseMapperFactory = mockk<AdoptReleaseMapperFactory>()
        every { adoptReleaseMapperFactory.get(any()) }.returns(
            object : ReleaseMapper() {
                override suspend fun toAdoptRelease(ghRelease: GHRelease): ReleaseResult {
                    return ReleaseResult(
                        listOf(
                            Release(
                                "foo", ReleaseType.ga, "a", "foo",
                                DateTime(ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, TimeSource.ZONE)),
                                DateTime(ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, TimeSource.ZONE)),
                                arrayOf(), 2, Vendor.getDefault(),
                                VersionData(8, 0, 242, "b", null, 4, "b", "")
                            ),
                        )
                    )
                }
            })

        val repository = AdoptRepositoryImpl(
            GraphQLGitHubClient(
                mockk<GraphQLGitHubSummaryClient>(),
                mockk<GraphQLGitHubReleaseClient>(),
                client,
                mockk<GraphQLGitHubCdxaSummaryClient>(),
                mockk<GraphQLGitHubCdxaClient>()
            ),
            adoptReleaseMapperFactory
        )
        return repository
    }
}
