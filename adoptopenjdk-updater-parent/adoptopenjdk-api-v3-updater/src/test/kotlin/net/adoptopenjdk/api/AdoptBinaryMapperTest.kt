package net.adoptopenjdk.api

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHVersion
import net.adoptopenjdk.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.Installer
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Package
import net.adoptopenjdk.api.v3.models.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

@TestInstance(Lifecycle.PER_CLASS)
class AdoptBinaryMapperTest {

    private val fakeGithubHtmlClient = mockk<GitHubHtmlClient>()
    private val adoptBinaryMapper = AdoptBinaryMapper(fakeGithubHtmlClient)

    @BeforeEach
    fun beforeEach() {
        clearMocks(fakeGithubHtmlClient)
    }

    val jdk = GHAsset(
        "OpenJDK8U-jre_x64_linux_hotspot-jfr_2019-11-21-10-26.tar.gz",
        1L,
        "",
        1L,
        "2013-02-27T19:35:32Z"
    )

    val assets = listOf(
        jdk,
        GHAsset(
            "OpenJDK8U-jdk_x64_linux_hotspot_2019-11-22-16-01.tar.gz",
            1L,
            "",
            1L,
            "2013-02-27T19:35:32Z"
        )
    )

    @Test
    fun `should map GitHub assets and metadata to Adopt binary`() {
        runBlocking {
            val updatedAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2013-02-27T19:35:32Z"))
                .atZone(TimeSource.ZONE)
            val updatedAtFormatted = DateTimeFormatter.ISO_INSTANT.format(updatedAt)

            val packageAsset = GHAsset(
                name = "archive.tar.gz",
                size = 1,
                downloadUrl = "http://package-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val packageChecksumAsset = GHAsset(
                name = "archive.tar.gz.sha256.txt",
                size = 1,
                downloadUrl = "http://package-checksum-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val packageMetadataAsset = GHAsset(
                name = "archive.tar.gz.json",
                size = 1,
                downloadUrl = "http://package-metadata-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val packageMetadata = GHMetaData(
                warning = "THIS METADATA FILE IS STILL IN ALPHA DO NOT USE ME",
                os = OperatingSystem.mac,
                arch = Architecture.x64,
                variant = "hotspot",
                version = GHVersion(0, 1, 2, "", 4, "", 6, "", "", null),
                scmRef = "scm-ref",
                version_data = "",
                binary_type = ImageType.jdk,
                sha256 = "package-checksum"
            )

            val installerAsset = GHAsset(
                name = "archive.msi",
                size = 1,
                downloadUrl = "http://installer-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val installerChecksumAsset = GHAsset(
                name = "archive.msi.sha256.txt",
                size = 1,
                downloadUrl = "http://installer-checksum-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val installerMetadataAsset = GHAsset(
                name = "archive.msi.json",
                size = 1,
                downloadUrl = "http://installer-metadata-link",
                downloadCount = 1,
                updatedAt = updatedAtFormatted
            )

            val installerMetadata = GHMetaData(
                warning = "THIS METADATA FILE IS STILL IN ALPHA DO NOT USE ME",
                os = OperatingSystem.mac,
                arch = Architecture.x64,
                variant = "hotspot",
                version = GHVersion(0, 1, 2, "", 4, "", 6, "", "", null),
                scmRef = "",
                version_data = "",
                binary_type = ImageType.jdk,
                sha256 = "installer-checksum"
            )

            val ghBinaryAssets = listOf(packageAsset, installerAsset)

            val fullGhAssetList = listOf(
                packageAsset,
                packageChecksumAsset,
                packageMetadataAsset,
                installerAsset,
                installerChecksumAsset,
                installerMetadataAsset
            )

            val ghBinaryAssetsWithMetadata: Map<GHAsset, GHMetaData> = mapOf(
                Pair(packageAsset, packageMetadata),
                Pair(installerAsset, installerMetadata)
            )

            coEvery { fakeGithubHtmlClient.getUrl("http://installer-checksum-link") } returns "installer-checksum archive.msi"

            val actualBinaries = adoptBinaryMapper.toBinaryList(ghBinaryAssets, fullGhAssetList, ghBinaryAssetsWithMetadata)

            val expectedBinary =
                Binary(
                    `package` = Package(
                        name = "archive.tar.gz",
                        link = "http://package-link",
                        size = 1,
                        checksum = "package-checksum",
                        checksum_link = "http://package-checksum-link",
                        download_count = 1,
                        signature_link = null,
                        metadata_link = "http://package-metadata-link"
                    ),
                    download_count = 2,
                    updated_at = DateTime(updatedAt),
                    scm_ref = "scm-ref",
                    installer = Installer(
                        name = "archive.msi",
                        link = "http://installer-link",
                        size = 1,
                        checksum = "installer-checksum",
                        checksum_link = "http://installer-checksum-link",
                        download_count = 1,
                        signature_link = null,
                        metadata_link = "http://installer-metadata-link"
                    ),
                    heap_size = HeapSize.normal,
                    os = OperatingSystem.mac,
                    architecture = Architecture.x64,
                    image_type = ImageType.jdk,
                    jvm_impl = JvmImpl.hotspot,
                    project = Project.jdk
                )

            assertEquals(expectedBinary, actualBinaries[0])
        }
    }

    @Test
    fun `old checksum is found`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.sha256.txt",
                    1L,
                    "a-download-link",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals("a-download-link", binaryList[0].`package`.checksum_link)
        }
    }

    @Test
    fun `parses old OpenJ9`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals(JvmImpl.openj9, binaryList[0].jvm_impl)
            assertEquals(Architecture.ppc64le, binaryList[0].architecture)
            assertEquals(OperatingSystem.linux, binaryList[0].os)
            assertEquals(Project.jdk, binaryList[0].project)
        }
    }

    @Test
    fun `parses JFR from name`() {
        runBlocking {
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())
            assertParsedHotspotJfr(binaryList)
        }
    }

    @Test
    fun `project defaults to jdk`() {
        runBlocking {
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())
            assertEquals(Project.jdk, binaryList[1].project)
        }
    }

    @Test
    fun `parses JFR from metadata`() {
        runBlocking {
            val metadata = GHMetaData(
                "", OperatingSystem.linux, Architecture.x64, "hotspot-jfr",
                GHVersion(0, 1, 2, "", 4, "", 6, "", "", null),
                "",
                "",
                ImageType.jdk,
                ""
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, mapOf(Pair(jdk, metadata)))
            assertParsedHotspotJfr(binaryList)
        }
    }

    @Test
    fun `checkSumLink found when checksum is split from release group`() {
        runBlocking {
            val asset = GHAsset(
                "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.tar.gz",
                1L,
                "",
                1L,
                "2013-02-27T19:35:32Z"
            )

            val checksum = GHAsset(
                "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.sha256.txt",
                1L,
                "a-download-link",
                1L,
                "2013-02-27T19:35:32Z"
            )

            val binaryList = adoptBinaryMapper.toBinaryList(listOf(asset), listOf(asset, checksum), emptyMap())

            assertEquals("a-download-link", binaryList[0].`package`.checksum_link)
        }
    }

    @Test
    fun `old large heap is correctly identified`() {
        runBlocking {
            val asset = GHAsset(
                "OPENJ9_x64_LinuxLH_jdk8u181-b13_openj9-0.9.0.tar.gz",
                1L,
                "",
                1L,
                "2013-02-27T19:35:32Z"
            )

            val binaryList = adoptBinaryMapper.toBinaryList(listOf(asset), listOf(asset), emptyMap())

            assertEquals(HeapSize.large, binaryList[0].heap_size)
        }
    }

    @Test
    fun `creates metadata link for package`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK11U-jdk_x64_linux_hotspot_11.0.8_10.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK11U-jdk_x64_linux_hotspot_11.0.8_10.tar.gz.json",
                    1L,
                    "a-download-link",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals("a-download-link", binaryList[0].`package`.metadata_link)
        }
    }

    @Test
    fun `identifies alpine-linux`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK-jdk_x64_alpine-linux_hotspot_2020-11-23-03-35.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals(OperatingSystem.`alpine-linux`, binaryList[0].os)
        }
    }

    private fun assertParsedHotspotJfr(binaryList: List<Binary>) {
        assertEquals(JvmImpl.hotspot, binaryList[0].jvm_impl)
        assertEquals(Project.jfr, binaryList[0].project)
    }
}
