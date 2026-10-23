package net.adoptium.api

import net.adoptium.api.v3.ReleaseFilterType
import net.adoptium.api.v3.ReleaseIncludeFilter
import com.expediagroup.graphql.client.types.GraphQLClientRequest
import com.expediagroup.graphql.client.types.GraphQLClientResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import net.adoptium.api.v3.AdoptRepositoryImpl
import net.adoptium.api.v3.AdoptReposBuilder
import net.adoptium.api.v3.V3Updater
import net.adoptium.api.v3.ReleaseResult
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.config.Ecosystem
import net.adoptium.api.v3.dataSources.UpdaterJsonMapper
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.GitHubApi
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
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleaseResult
import net.adoptium.api.v3.dataSources.github.graphql.models.GHReleases
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRepository
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.github.graphql.models.QueryData
import net.adoptium.api.v3.dataSources.github.graphql.models.QuerySummaryData
import net.adoptium.api.v3.dataSources.github.graphql.models.RateLimit
import net.adoptium.api.v3.dataSources.github.graphql.models.ReleaseQueryData
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHAssetsSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleaseSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHReleasesSummary
import net.adoptium.api.v3.dataSources.github.graphql.models.summary.GHRepositorySummary
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.mapping.ReleaseMapper
import net.adoptium.api.v3.mapping.ReleaseMapper.Companion.parseDate
import net.adoptium.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.exc.MismatchedInputException
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
                    assertTrue(Regex("nodes\\s*\\{\\s*id,\\s*digest,").containsMatchIn(query.query.orEmpty()))
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

                    assertTrue(Regex("nodes\\s*\\{\\s*id,\\s*digest,").containsMatchIn(query.query.orEmpty()))
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

    @Test
    fun `asset identity and digest are preserved across asset pages`() = runBlocking {
        val firstAsset = source.assets.first().copy(id = "asset-first", digest = "sha256:first")
        val secondAsset = source.assets.last().copy(id = "asset-second", digest = "sha256:second")
        val firstPage = response.copy(releaseAssets = GHAssets(listOf(firstAsset), PageInfo(true, "asset-page-2"), 2))
        var requests = 0
        val graphQLRequest = object : GraphQLRequest {
            override suspend fun <F : Any> request(query: GraphQLClientRequest<F>): GraphQLClientResponse<F> {
                requests++
                assertTrue(Regex("nodes\\s*\\{\\s*id,\\s*digest,").containsMatchIn(query.query.orEmpty()))
                val data = if (query.responseType() == GHReleaseResult::class) {
                    GHReleaseResult(firstPage, RateLimit(0, 5000))
                } else {
                    assertEquals("asset-page-2", (query.variables as Map<String, String>)["cursorPointer"])
                    ReleaseQueryData(
                        GHAssetNode(GHAssets(listOf(secondAsset), PageInfo(false, null), 2)), RateLimit(0, 5000)
                    )
                }
                val result = mockk<GraphQLClientResponse<F>>()
                every { result.data } returns data as F
                every { result.errors } returns null
                return result
            }
        }
        val graphQL = GraphQLGitHubInterface(graphQLRequest, mockkHttpClient())
        val client = GraphQLGitHubReleaseClient(graphQL, GraphQLGitHubReleaseRequest(graphQL))

        val assets = client.getReleaseById(GitHubId("1"))!!.releaseAssets.assets

        assertEquals(listOf(firstAsset, secondAsset), assets)
        assertEquals(2, requests)
    }

    @ParameterizedTest
    @ValueSource(strings = ["first-null", "next-null", "first-no-data", "next-no-data", "first-decode", "next-decode", "next-missing-repo"])
    fun `summary pagination rejects missing pages instead of returning a partial catalog`(failure: String) {
        val request = mockk<GraphQLRequest>()
        val graphQL = spyk(GraphQLGitHubInterface(request, mockkHttpClient()))
        val firstPage = mockk<GraphQLClientResponse<QuerySummaryData>>()
        every { firstPage.data } returns QuerySummaryData(
            GHRepositorySummary(GHReleasesSummary(
                listOf(GHReleaseSummary(GitHubId("release-1"), "jdk8", "2013-02-27T19:35:32Z",
                    "2013-02-27T19:35:32Z", GHAssetsSummary(2))),
                PageInfo(true, "next-page")
            )), RateLimit(0, 5000)
        )
        every { firstPage.errors } returns null
        val emptyResponse = mockk<GraphQLClientResponse<QuerySummaryData>>()
        every { emptyResponse.data } returns null
        every { emptyResponse.errors } returns if (failure == "next-missing-repo") {
            listOf(mockk { every { message } returns "Could not resolve to a Repository" })
        } else null
        val failedPage = if (failure.endsWith("-null")) null else emptyResponse
        if (failure.endsWith("-decode")) {
            val decodeFailure = MismatchedInputException.from(null, QuerySummaryData::class.java, "Invalid GraphQL summary")
            coEvery { request.request(any<GraphQLClientRequest<QuerySummaryData>>()) } throws decodeFailure
            if (failure.startsWith("next-")) {
                coEvery { graphQL.queryApi<QuerySummaryData>(any(), null) } returns firstPage
            }
        } else {
            coEvery { graphQL.queryApi<QuerySummaryData>(any(), null) } returns
                if (failure.startsWith("first-")) failedPage else firstPage
            coEvery { graphQL.queryApi<QuerySummaryData>(any(), "next-page") } returns failedPage
        }
        val client = GraphQLGitHubSummaryClient(graphQL)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.getRepositorySummary("adoptium", "temurin8-binaries") }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["null", "no-data", "decode"])
    fun `asset pagination rejects missing continuation instead of returning a partial snapshot`(failure: String) {
        val request = mockk<GraphQLRequest>()
        val graphQL = spyk(GraphQLGitHubInterface(request, mockkHttpClient()))
        val firstPage = mockk<GraphQLClientResponse<GHReleaseResult>>()
        every { firstPage.data } returns GHReleaseResult(
            response.copy(releaseAssets = source.copy(pageInfo = PageInfo(true, "next-assets"))), RateLimit(0, 5000)
        )
        every { firstPage.errors } returns null
        val emptyResponse = mockk<GraphQLClientResponse<ReleaseQueryData>>()
        every { emptyResponse.data } returns null
        every { emptyResponse.errors } returns null
        coEvery { graphQL.queryApi<GHReleaseResult>(any(), null) } returns firstPage
        if (failure == "decode") {
            val decodeFailure = MismatchedInputException.from(null, ReleaseQueryData::class.java, "Invalid GraphQL assets")
            coEvery { request.request(any<GraphQLClientRequest<ReleaseQueryData>>()) } throws decodeFailure
        } else {
            coEvery { graphQL.queryApi<ReleaseQueryData>(any(), "next-assets") } returns
                if (failure == "null") null else emptyResponse
        }
        val client = GraphQLGitHubReleaseClient(graphQL, GraphQLGitHubReleaseRequest(graphQL))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.getReleaseById(GitHubId("1")) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing", "blank", "repeated"])
    fun `summary pagination rejects invalid continuation cursors`(failure: String) {
        val cursor = when (failure) {
            "missing" -> null
            "blank" -> ""
            else -> "same-page"
        }
        val graphQL = spyk(GraphQLGitHubInterface(mockk(), mockkHttpClient()))
        val page = mockk<GraphQLClientResponse<QuerySummaryData>>()
        every { page.data } returns QuerySummaryData(
            GHRepositorySummary(GHReleasesSummary(emptyList(), PageInfo(true, cursor))), RateLimit(0, 5000)
        )
        every { page.errors } returns null
        coEvery { graphQL.queryApi<QuerySummaryData>(any(), any()) } returns page

        assertThrows(IllegalStateException::class.java) {
            runBlocking { GraphQLGitHubSummaryClient(graphQL).getRepositorySummary("adoptium", "temurin8-binaries") }
        }
        coVerify(exactly = if (failure == "repeated") 2 else 1) {
            graphQL.queryApi<QuerySummaryData>(any(), any())
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `full builds propagate GA mapping exceptions and cancellation`(cancelled: Boolean) {
        val gitHub = mockk<GitHubApi>()
        val mapper = mockk<ReleaseMapper>()
        val factory = mockk<AdoptReleaseMapperFactory>()
        every { factory.get(any()) } returns mapper
        val upstream = response.copy(isPrerelease = cancelled, url = "https://github.com/adoptium/temurin11-binaries/releases/tag/ea")
        coEvery { gitHub.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(
                if (secondArg<String>() == "temurin11-binaries") listOf(upstream) else emptyList(), PageInfo(false, null)
            ))
        }
        val failure = if (cancelled) CancellationException("Cancelled") else IllegalStateException("Mapping failed")
        coEvery { mapper.toAdoptRelease(upstream) } throws failure
        val repository = AdoptRepositoryImpl(gitHub, factory)

        val thrown = assertThrows(failure.javaClass) {
            runBlocking { repository.getRelease(11, ReleaseIncludeFilter.INCLUDE_ALL) }
        }

        assertEquals(failure.message, thrown.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["exception", "retryable", "assets", "new", "empty", "excluded"])
    fun `full builds omit failed EAs and replace successful snapshots`(outcome: String) = runBlocking {
        val gitHub = mockk<GitHubApi>()
        val mapper = mockk<ReleaseMapper>()
        val factory = mockk<AdoptReleaseMapperFactory>()
        every { factory.get(any()) } returns mapper
        fun upstream(id: String, ea: Boolean) = response.copy(
            id = GitHubId(id), isPrerelease = ea,
            url = "https://github.com/adoptium/temurin11-binaries/releases/tag/$id"
        )
        fun split(id: String, ea: Boolean = true, name: String = id) = Release(
            id, if (ea) ReleaseType.ea else ReleaseType.ga, "link", name,
            DateTime(TimeSource.now()), DateTime(TimeSource.now()), emptyArray(), 20, Vendor.eclipse,
            VersionData(11, 0, 13, null, null, 8, null, "")
        )
        val first = upstream("first", false)
        val failed = upstream("failed", true).let {
            if (outcome == "assets") it.copy(releaseAssets = source.copy(pageInfo = PageInfo(true, "unfinished"))) else it
        }
        val last = upstream("last", true)
        val firstMapped = split("first", false)
        val lastMapped = split("last")
        val oldSplits = listOf(split("failed.1"), split("failed.2"))
        val prefixCollision = split("failed-other.1")
        val prior = if (outcome == "new") emptyList() else oldSplits + prefixCollision
        coEvery { gitHub.getRepository(any(), any(), any()) } answers {
            GHRepository(GHReleases(
                if (secondArg<String>() == "temurin11-binaries") listOf(first, failed, last) else emptyList(),
                PageInfo(false, null)
            ))
        }
        coEvery { mapper.toAdoptRelease(first) } returns ReleaseResult(listOf(firstMapped))
        coEvery { mapper.toAdoptRelease(last) } returns ReleaseResult(listOf(lastMapped))
        when (outcome) {
            "exception" -> coEvery { mapper.toAdoptRelease(failed) } throws IllegalStateException("Mapping failed")
            "empty" -> coEvery { mapper.toAdoptRelease(failed) } returns ReleaseResult(emptyList())
            "excluded" -> coEvery { mapper.toAdoptRelease(failed) } returns ReleaseResult(listOf(split("failed", name = "jdk-11.0.13'+8")))
            else -> coEvery { mapper.toAdoptRelease(failed) } returns ReleaseResult(error = "Metadata failed", retryable = true)
        }
        val versions = mockk<VersionSupplier>()
        every { versions.getAllVersions() } returns arrayOf(11)
        val before = AdoptRepos(listOf(FeatureRelease(11, Releases(prior))))
        val builder = AdoptReposBuilder(AdoptRepositoryImpl(gitHub, factory), versions)

        val fresh = builder.build(ReleaseIncludeFilter.INCLUDE_ALL)
        val result = V3Updater.copyOldReleasesIntoNewRepo(before, fresh, ReleaseIncludeFilter.INCLUDE_ALL)

        assertEquals(listOf(firstMapped, lastMapped).associateBy { it.id },
            result.getFeatureRelease(11)!!.releases.nodes)
        coVerify(exactly = if (outcome == "assets") 0 else 1) { mapper.toAdoptRelease(failed) }
        if (outcome in setOf("exception", "retryable", "assets", "new")) {
            val recovered = split("failed.new")
            val complete = failed.copy(releaseAssets = source)
            coEvery { gitHub.getRepository(any(), any(), any()) } answers {
                GHRepository(GHReleases(
                    if (secondArg<String>() == "temurin11-binaries") listOf(first, complete, last) else emptyList(),
                    PageInfo(false, null)
                ))
            }
            coEvery { mapper.toAdoptRelease(complete) } returns ReleaseResult(listOf(recovered))
            val after = builder.build(ReleaseIncludeFilter.INCLUDE_ALL)
            assertEquals(listOf(firstMapped, recovered, lastMapped).associateBy { it.id },
                after.getFeatureRelease(11)!!.releases.nodes)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ea", "ga", "cancelled"])
    fun `repository asset pagination isolates only ordinary EA failures`(kind: String) = runBlocking {
        val graphQL = spyk(GraphQLGitHubInterface(mockk(), mockkHttpClient()))
        val assets = mockk<GraphQLGitHubReleaseRequest>()
        val failed = response.copy(
            isPrerelease = kind != "ga", releaseAssets = source.copy(pageInfo = PageInfo(true, "next"))
        )
        val healthy = response.copy(id = GitHubId("healthy"))
        val page = mockk<GraphQLClientResponse<QueryData>>()
        every { page.data } returns QueryData(
            GHRepository(GHReleases(listOf(failed, healthy), PageInfo(false, null))), RateLimit(0, 5000)
        )
        every { page.errors } returns null
        coEvery { graphQL.queryApi<QueryData>(any(), null) } returns page
        val failure = if (kind == "cancelled") CancellationException("Cancelled") else IllegalStateException("Asset page failed")
        coEvery { assets.getAllReleaseAssets(failed) } throws failure
        val client = GraphQLGitHubRepositoryClient(graphQL, assets)

        if (kind == "ea") {
            val result = client.getRepository("adoptium", "temurin11-binaries") { _, _ -> true }
            assertEquals(listOf(failed, healthy), result.getReleases())
            assertTrue(result.getReleases().first().releaseAssets.pageInfo.hasNextPage)
        } else {
            val thrown = assertThrows(failure.javaClass) {
                runBlocking { client.getRepository("adoptium", "temurin11-binaries") { _, _ -> true } }
            }
            assertEquals(failure.message, thrown.message)
        }
    }

    @Test
    fun `asset responses deserialize with and without identity fields`() {
        val fields = """"name":"checksum","size":1,"downloadUrl":"url","downloadCount":0,"updatedAt":"2013-02-27T19:35:32Z""""
        val legacy = UpdaterJsonMapper.mapper.readValue("{$fields}", GHAsset::class.java)
        val current = UpdaterJsonMapper.mapper.readValue(
            """{$fields,"id":"asset-id","digest":"sha256:digest"}""", GHAsset::class.java
        )

        assertEquals(null, legacy.id)
        assertEquals(null, legacy.digest)
        assertEquals("asset-id", current.id)
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
    @ValueSource(booleans = [true, false])
    fun `incremental mapping applies the same EA exclusions as full updates`(ea: Boolean) = runBlocking {
        val gitHub = mockk<GitHubApi>()
        val mapper = mockk<ReleaseMapper>()
        val factory = mockk<AdoptReleaseMapperFactory>()
        every { factory.get(any()) } returns mapper
        val repository = AdoptRepositoryImpl(gitHub, factory)
        val type = if (ea) ReleaseType.ea else ReleaseType.ga

        for (name in listOf("jdk-11.0.13'+8", "jdk17u-2022-05-27-19-32-beta", "jdk-11.0.13+8")) {
            val upstream = response.copy(name = name, isPrerelease = ea,
                url = "https://github.com/adoptium/temurin11-binaries/releases/tag/$name")
            val split = mockk<Release>()
            every { split.id } returns "1.split"
            every { split.release_name } returns name
            every { split.release_type } returns type
            every { split.vendor } returns Vendor.eclipse
            every { split.release_link } returns upstream.url
            every { split.version_data } returns VersionData(11, 0, 13, null, null, 8, null, "")
            every { split.timestamp } returns DateTime(parseDate("2013-02-27T19:35:32Z"))
            every { split.updated_at } returns DateTime(parseDate("2013-02-27T19:35:32Z"))
            coEvery { mapper.toAdoptRelease(upstream) } returns ReleaseResult(listOf(split))
            coEvery { gitHub.getReleaseById(upstream.id) } returns upstream
            coEvery { gitHub.getRepository(any(), any(), any()) } answers {
                GHRepository(GHReleases(
                    if (secondArg<String>() == "temurin11-binaries") listOf(upstream) else emptyList(),
                    PageInfo(false, null)
                ))
            }
            coEvery { gitHub.getRepositorySummary(any(), any()) } answers {
                GHSummaryTestDataGenerator.generateGHRepositorySummary(GHRepository(GHReleases(
                    if (secondArg<String>() == "temurin11-binaries") listOf(upstream) else emptyList(),
                    PageInfo(false, null)
                )))
            }
            val expected = if (ea && name != "jdk-11.0.13+8") emptyList() else listOf(split)

            val incremental = repository.getReleaseById(upstream.id)!!
            val full = repository.getRelease(11, ReleaseIncludeFilter.INCLUDE_ALL)

            assertTrue(incremental.succeeded())
            assertEquals(expected, incremental.result)
            assertEquals(expected, full.releases.getReleases().toList())

            val before = AdoptRepos(listOf(FeatureRelease(11, Releases(listOf(split)))))
            val after = AdoptReposBuilder(repository, mockk<VersionSupplier> {
                every { getAllVersions() } returns arrayOf(11)
            })
                .incrementalUpdate(setOf(name), before) { null }

            assertEquals(expected, after.getFeatureRelease(11)!!.releases.getReleases().toList())
        }
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
