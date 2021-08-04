package net.adoptopenjdk.api

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.testDoubles.InMemoryInternalDbStore
import net.adoptopenjdk.api.v3.dataSources.DefaultUpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.HttpClientFactory
import net.adoptopenjdk.api.v3.dataSources.UpdaterHtmlClient
import net.adoptopenjdk.api.v3.dataSources.UrlRequest
import net.adoptopenjdk.api.v3.dataSources.github.CachedGitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAssets
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHRelease
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.PageInfo
import net.adoptopenjdk.api.v3.dataSources.models.GitHubId
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptReleaseMapperFactory
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.ProtocolVersion
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicStatusLine
import org.jboss.weld.junit5.auto.AddPackages
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@AddPackages(value = [DefaultUpdaterHtmlClient::class, HttpClientFactory::class])
class SemeruReleaseMapperTest : BaseTest() {

    @Test
    fun `parses release`() {
        runBlocking {
            val assets = GHAssets(
                SemeruMapperTest.allGhAssets,
                PageInfo(false, "")
            )

            val client = object : UpdaterHtmlClient {
                override suspend fun get(url: String): String? {
                    return getMetadata(url)
                }

                fun getMetadata(url: String): String {
                    return """{
                        "vendor": "IBM",
                        "os": "linux",
                        "arch": "s390x",
                        "variant": "openj9",
                        "version": {
                            "minor": 0,
                            "patch": null,
                            "msi_product_version": "8.0.302.8",
                            "security": 302,
                            "pre": null,
                            "adopt_build_number": null,
                            "major": 8,
                            "version": "1.8.0_302-b08",
                            "semver": "8.0.302+8",
                            "build": 8,
                            "opt": null
                        },
                        "scmRef": "v0.27.0-release",
                        "buildRef": "https://github.com/ibmruntimes/temurin-build/commit/11a5243",
                        "version_data": "jdk8u",
                        "binary_type": "debugimage",
                        "sha256": "df4a9e9e5ea9f225462462287b79e50a3bc98bc8211530da3f30f77d2d759ca4",
                        "full_version_output": "",
                        "makejdk_any_platform_args": "",
                        "configure_arguments": "",
                        "make_command_args": "",
                        "BUILD_CONFIGURATION_param": "",
                        "openjdk_built_config": "",
                        "openjdk_source": "https://github.com/ibmruntimes/openj9-openjdk-jdk8/commit/de702c3174",
                        "build_env_docker_image_digest": "",
                        "dependency_version_alsa": "https://ftp.osuosl.org/pub/blfs/conglomeration/alsa-lib/alsa-lib-1.1.6.tar.bz2\n",
                        "dependency_version_freetype": "",
                        "dependency_version_freemarker": "https://www.apache.org/dist/freemarker/engine/2.3.31/binaries/apache-freemarker-2.3.31-bin.tar.gz\n",
                        "variant_version": {
                            "major": "0",
                            "minor": "0",
                            "security": "0",
                            "tags": "27"
                        }
                    }
                    """.trimIndent()
                        .replace("\n", "")
                }

                override suspend fun getFullResponse(request: UrlRequest): HttpResponse? {

                    val metadataResponse = mockk<HttpResponse>()

                    val entity = mockk<HttpEntity>()
                    every { entity.content } returns getMetadata(request.url).byteInputStream()
                    every { metadataResponse.statusLine } returns BasicStatusLine(ProtocolVersion("", 1, 1), 200, "")
                    every { metadataResponse.entity } returns entity
                    every { metadataResponse.getFirstHeader("Last-Modified") } returns BasicHeader("Last-Modified", "Thu, 01 Jan 1970 00:00:00 GMT")
                    return metadataResponse
                }
            }

            val ghRelease = GHRelease(
                id = GitHubId("1"),
                name = "jdk8u302-b08_openj9-0.27.0",
                isPrerelease = false,
                publishedAt = "2013-02-27T19:35:32Z",
                updatedAt = "2013-02-27T19:35:32Z",
                releaseAssets = assets,
                resourcePath = "8",
                url = "https://github.com/ibmruntimes/semeru8-binaries/releases/tag/jdk8u302-b08_openj9-0.27.0"
            )

            val cachedGitHubHtmlClient = CachedGitHubHtmlClient(InMemoryInternalDbStore(), client)
            val release = AdoptReleaseMapperFactory(AdoptBinaryMapper(cachedGitHubHtmlClient), cachedGitHubHtmlClient).get(Vendor.ibm).toAdoptRelease(ghRelease)

            assertEquals(1, release.result!!.size)
            assertEquals(1, release.result!![0].binaries.size)
            assertEquals("jdk8u302-b08_openj9-0.27.0", release.result!![0].release_name)
            assertEquals("https://github.com/ibmruntimes/semeru8-binaries/releases/tag/jdk8u302-b08_openj9-0.27.0", release.result!![0].release_link)
            assertEquals(ReleaseType.ga, release.result!![0].release_type)
            assertEquals(Vendor.ibm, release.result!![0].vendor)
            assertEquals(
                VersionData(
                    8,
                    0,
                    302,
                    null,
                    null,
                    8,
                    null,
                    "1.8.0_302-b08",
                    "8.0.302+8"
                ), release.result!![0].version_data
            )

            assertEquals(OperatingSystem.linux, release.result!![0].binaries[0].os)
            assertEquals(Architecture.s390x, release.result!![0].binaries[0].architecture)
            assertEquals(HeapSize.normal, release.result!![0].binaries[0].heap_size)
            assertEquals(ImageType.debugimage, release.result!![0].binaries[0].image_type)
            assertEquals(JvmImpl.openj9, release.result!![0].binaries[0].jvm_impl)
            assertEquals("df4a9e9e5ea9f225462462287b79e50a3bc98bc8211530da3f30f77d2d759ca4", release.result!![0].binaries[0].`package`.checksum)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz", release.result!![0].binaries[0].`package`.link)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.sha256.txt", release.result!![0].binaries[0].`package`.checksum_link)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.json", release.result!![0].binaries[0].`package`.metadata_link)
            assertEquals(Project.jdk, release.result!![0].binaries[0].project)
        }
    }

}
