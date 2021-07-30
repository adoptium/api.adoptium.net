package net.adoptopenjdk.api

import io.mockk.clearMocks
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import kotlin.test.assertEquals

@TestInstance(Lifecycle.PER_CLASS)
class SemeruMapperTest {

    private val fakeGithubHtmlClient = mockk<GitHubHtmlClient>()
    private val semeruBinaryMapper = AdoptBinaryMapper(fakeGithubHtmlClient)

    companion object {

        val data = JsonMapper.mapper.readValue("""{
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
            }""".trimIndent(), GHMetaData::class.java
        )

        val ghBinaryAssets: List<GHAsset> = makeAssets(listOf("ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz"));

        val allGhAssets: List<GHAsset> = makeAssets(listOf(
            "ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz",
            "ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.json",
            "ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.sha256.txt"
        )
        )

        val ghAssetsWithMetadata: Map<GHAsset, GHMetaData> = mapOf(
            Pair(ghBinaryAssets[0], data)
        )

        private fun makeAssets(listOf: List<String>): List<GHAsset> {
            return listOf
                .map { entry ->
                    GHAsset(
                        entry,
                        0,
                        "url-for-${entry}",
                        0,
                        "2013-02-27T19:35:32Z"
                    )
                }
        }
    }

    @BeforeEach
    fun beforeEach() {
        clearMocks(fakeGithubHtmlClient)
    }

    @Test
    fun `parses semeru binary data`() {

        runBlocking {
            val parsed = semeruBinaryMapper
                .toBinaryList(ghBinaryAssets, allGhAssets, ghAssetsWithMetadata)
            assertEquals(OperatingSystem.linux, parsed[0].os)
            assertEquals(Architecture.s390x, parsed[0].architecture)
            assertEquals(HeapSize.normal, parsed[0].heap_size)
            assertEquals(ImageType.debugimage, parsed[0].image_type)
            assertEquals(JvmImpl.openj9, parsed[0].jvm_impl)
            assertEquals("df4a9e9e5ea9f225462462287b79e50a3bc98bc8211530da3f30f77d2d759ca4", parsed[0].`package`.checksum)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz", parsed[0].`package`.link)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.sha256.txt", parsed[0].`package`.checksum_link)
            assertEquals("url-for-ibm-semeru-open-debugimage_s390x_linux_8u302b08_openj9-0.27.0.tar.gz.json", parsed[0].`package`.metadata_link)
            assertEquals(Project.jdk, parsed[0].project)
        }
    }


}
