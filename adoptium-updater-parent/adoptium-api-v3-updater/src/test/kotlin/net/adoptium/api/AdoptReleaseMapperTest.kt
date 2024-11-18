package net.adoptium.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptium.api.v3.dataSources.HttpClientFactory
import net.adoptium.api.v3.dataSources.UpdaterHtmlClient
import net.adoptium.api.v3.dataSources.UrlRequest
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptium.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptium.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptium.api.v3.dataSources.models.GitHubId
import net.adoptium.api.v3.models.ReleaseType
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

@AddPackages(value = [DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class AdoptReleaseMapperTest : BaseTest() {

    val jdk = GHAsset(
        name = "OpenJDK8U-jre_x64_linux_hotspot-123244354325.tar.gz",
        size = 1L,
        downloadUrl = "",
        downloadCount = 1L,
        updatedAt = "2013-02-27T19:35:32Z"
    )

    val checksum = GHAsset(
        name = "OpenJDK8U-jre_x64_linux_hotspot-123244354325.tar.gz.sha256.txt",
        size = 1L,
        downloadUrl = "",
        downloadCount = 1L,
        updatedAt = "2013-02-27T19:35:32Z"
    )

    @Test
    fun ignoresUnparsableVersion() {
        runBlocking {
            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = GHRelease(
                id = GitHubId("1"),
                name = "OpenJDK 123244354325",
                isPrerelease = true,
                publishedAt = "2013-02-27T19:35:32Z",
                updatedAt = "2013-02-27T19:35:32Z",
                releaseAssets = source,
                resourcePath = "8",
                url = "a-url"
            )

            val result = createAdoptReleaseMapper().toAdoptRelease(ghRelease)
            assertFalse(result.succeeded())
            assertNotNull(result.error)
            assertNull(result.result)
        }
    }

    @Test
    fun statsIgnoresNonBinaryAssets() {
        runBlocking {
            val source = GHAssets(listOf(jdk, checksum), PageInfo(false, ""), 2)

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals(1, release.result!!.first().download_count)
        }
    }

    @Test
    fun obeysReleaseTypeforBinaryRepos() {
        runBlocking {

            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals(ReleaseType.ea, release.result!!.first().release_type)
        }
    }

    @Test
    fun copesWithSbom() {
        runBlocking {
            val source = buildGhAssets(
                listOf(
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json", "1"),
                    Pair("OpenJDK8U-sbom_aarch64_alpine-linux_hotspot_2022-06-17-23-30.json", ""),
                    Pair("OpenJDK8U-sbom_aarch64_alpine-linux_hotspot_2022-06-17-23-30-metadata.json", "2"),
                )
            )
            val ghRelease = formGhRelease(source)
            val release = createAdoptReleaseMapper(mockClient()).toAdoptRelease(ghRelease)

            assertEquals(2, release.result?.size)
        }
    }

    @Test
    fun copesWithMultipleVersionsInSingleRelease() {
        runBlocking {

            val source = buildGhAssets(
                listOf(
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-1.tar.gz.json", "1"),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-2.tar.gz.json", "2"),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-3.tar.gz", ""),
                    Pair("OpenJDK8U-jre_x64_linux_hotspot-3.tar.gz.json", "2"),
                )
            )
            val ghRelease = formGhRelease(source)

            val client = mockClient()

            val release = createAdoptReleaseMapper(client).toAdoptRelease(ghRelease)

            assertEquals(2, release.result!!.size)
            assertEquals(1, release.result!![0].binaries.size)
            assertEquals(2, release.result!![1].binaries.size)

            assertEquals(1, release.result!![0].download_count)
            assertEquals(2, release.result!![1].download_count)
        }
    }

    private fun mockClient() = object : UpdaterHtmlClient {
        override suspend fun get(url: String): String {
            return getMetadata(url)
        }

        fun getMetadata(url: String): String {
            val opt = UUID.randomUUID()

            val build = try {
                Integer.parseInt(url)
            } catch (ignore: Exception) {
                1
            }

            return """
                            {
                                "WARNING": "THIS METADATA FILE IS STILL IN ALPHA DO NOT USE ME",
                                "os": "windows",
                                "arch": "x86-32",
                                "variant": "openj9",
                                "version": {
                                    "minor": 0,
                                    "security": 242,
                                    "pre": null,
                                    "adopt_build_number": 1,
                                    "major": 8,
                                    "version": "1.8.0_242-$opt-b0$build",
                                    "semver": "8.0.242+$build.1.$opt",
                                    "build": $build,
                                    "opt": "$opt"
                                },
                                "scmRef": "",
                                "version_data": "jdk8u",
                                "binary_type": "jre",
                                "sha256": "dc755cf762c867d4c71b782b338d2dc1500b468ab01adbf88620b5ae55eef42a"
                            }
                        """.trimIndent()
                .replace("\n", "")
        }

        override suspend fun getFullResponse(request: UrlRequest): HttpResponse {

            val metadataResponse = mockk<HttpResponse>()

            val entity = mockk<HttpEntity>()
            every { entity.content } returns getMetadata(request.url).byteInputStream()
            every { metadataResponse.statusLine } returns BasicStatusLine(ProtocolVersion("", 1, 1), 200, "")
            every { metadataResponse.entity } returns entity
            every { metadataResponse.getFirstHeader("Last-Modified") } returns BasicHeader("Last-Modified", "Thu, 01 Jan 1970 00:00:00 GMT")
            return metadataResponse
        }
    }

    private fun buildGhAssets(assetNames: List<Pair<String, String>>) =
        GHAssets(assetNames
            .map {
                GHAsset(
                    it.first,
                    1L,
                    it.second,
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            },
            PageInfo(false, ""),
            assetNames.size
        )

    @Test
    fun updaterCopesWithExceptionFromGitHub() {
        runBlocking {

            val client = object : UpdaterHtmlClient {
                override suspend fun get(url: String): String? {
                    throw RuntimeException("Failed to get metadata")
                }

                override suspend fun getFullResponse(request: UrlRequest): HttpResponse? {
                    throw RuntimeException("Failed to get metadata")
                }
            }

            val source = GHAssets(listOf(jdk), PageInfo(false, ""), 1)

            val ghRelease = formGhRelease(source)

            createAdoptReleaseMapper(client).toAdoptRelease(ghRelease)
        }
    }

    @Test
    fun `identifies the source repo if present in a release`() {
        runBlocking {
            val source = GHAssets(
                listOf(
                    GHAsset(
                        "OpenJDK11U-sources_11.0.13_6_ea.tar.gz",
                        1L,
                        "the-source-link",
                        1L,
                        "2013-02-27T19:35:32Z"
                    )
                ),
                PageInfo(false, ""),
                1
            )

            val ghRelease = formGhRelease(source)

            val release = createAdoptReleaseMapper().toAdoptRelease(ghRelease)

            assertEquals("the-source-link", release.result!!.first().source!!.link)
        }
    }

    private fun formGhRelease(source: GHAssets) = GHRelease(
        id = GitHubId("1"),
        name = "jdk9u-2018-09-27-08-50",
        isPrerelease = true,
        publishedAt = "2013-02-27T19:35:32Z",
        updatedAt = "2013-02-27T19:35:32Z",
        releaseAssets = source,
        resourcePath = "8",
        url = "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk9u-2018-09-27-08-50/OpenJDK9U-jre_aarch64_linux_hotspot_2018-09-27-08-50.tar.gz"
    )
}
