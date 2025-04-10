package net.adoptium.api

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.github.GitHubHtmlClient
import net.adoptium.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptium.api.v3.dataSources.github.graphql.models.GHMetaData
import net.adoptium.api.v3.dataSources.github.graphql.models.GHVersion
import net.adoptium.api.v3.mapping.adopt.AdoptBinaryMapper
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.Installer
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Package
import net.adoptium.api.v3.models.Project
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals

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
    fun `detects signature link`() {
        runBlocking {
            val asset = GHAsset(
                "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.tar.gz",
                1L,
                "",
                1L,
                "2013-02-27T19:35:32Z"
            )

            val signature = GHAsset(
                "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.tar.gz.sig",
                1L,
                "a-download-link",
                1L,
                "2013-02-27T19:35:32Z"
            )

            val installerAsset = GHAsset(
                name = "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.msi",
                size = 1,
                downloadUrl = "http://installer-link",
                downloadCount = 1,
                "2013-02-27T19:35:32Z"
            )

            val installerAssetSig = GHAsset(
                name = "OpenJDK9-OPENJ9_ppc64le_Linux_jdk-9.0.4.12_openj9-0.9.0.msi.sig",
                size = 1,
                downloadUrl = "http://signature-link",
                downloadCount = 1,
                "2013-02-27T19:35:32Z"
            )

            val binaryList = adoptBinaryMapper.toBinaryList(listOf(asset), listOf(asset, signature, installerAsset, installerAssetSig), emptyMap())

            assertEquals("a-download-link", binaryList[0].`package`.signature_link)
            assertEquals("http://signature-link", binaryList[0].installer?.signature_link)
        }
    }

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
                    project = Project.jdk,
                    c_lib = null
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

    @Test
    fun `identifies static libs`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK11U-static-libs-glibc_x64_linux_hotspot_2021-09-28-08-28.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK11U-static-libs-musl_x64_linux_hotspot_2021-09-28-08-28.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "OpenJDK16U-jre_x64_linux_hotspot_16.0.1_9.tar.gz",
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals(ImageType.staticlibs, binaryList[0].image_type)
            assertEquals(ImageType.staticlibs, binaryList[1].image_type)
            assertNotEquals(ImageType.staticlibs, binaryList[2].image_type)
            assertEquals(CLib.glibc, binaryList[0].c_lib)
            assertEquals(CLib.musl, binaryList[1].c_lib)
            assertEquals(null, binaryList[2].c_lib)
        }
    }

    @Test
    fun `should trust file name over metadata file if the file name indicates a static-lib`() {
        runBlocking {
            val updatedAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse("2013-02-27T19:35:32Z"))
                .atZone(TimeSource.ZONE)
            val updatedAtFormatted = DateTimeFormatter.ISO_INSTANT.format(updatedAt)

            val packageAsset = GHAsset(
                name = "OpenJDK11U-static-libs-musl_x64_linux_hotspot_2021-09-28-08-28.tar.gz",
                size = 1,
                downloadUrl = "http://package-link",
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

            val ghBinaryAssetsWithMetadata: Map<GHAsset, GHMetaData> = mapOf(
                Pair(packageAsset, packageMetadata)
            )

            val actualBinaries = adoptBinaryMapper.toBinaryList(listOf(packageAsset), listOf(packageAsset), ghBinaryAssetsWithMetadata)

            val expectedBinary =
                Binary(
                    `package` = Package(
                        name = "OpenJDK11U-static-libs-musl_x64_linux_hotspot_2021-09-28-08-28.tar.gz",
                        link = "http://package-link",
                        size = 1,
                        checksum = "package-checksum",
                        checksum_link = null,
                        download_count = 1,
                        signature_link = null,
                        metadata_link = null
                    ),
                    download_count = 1,
                    updated_at = DateTime(updatedAt),
                    scm_ref = "scm-ref",
                    installer = null,
                    heap_size = HeapSize.normal,
                    os = OperatingSystem.mac,
                    architecture = Architecture.x64,
                    image_type = ImageType.staticlibs,
                    jvm_impl = JvmImpl.hotspot,
                    project = Project.jdk,
                    c_lib = CLib.musl
                )

            assertEquals(expectedBinary, actualBinaries[0])
        }
    }

    @Test
    fun `identifies sbom`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK18U-sbom_x64_linux_hotspot_2022-06-20-23-30.json",
                    1L,
                    "",
                    1L,
                    "2022-03-29T18:34:31Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals(ImageType.sbom, binaryList[0].image_type)
        }
    }

    @Test
    fun `identifies JMOD images`() {
        runBlocking {
            val assets = listOf(
                GHAsset(
                    "OpenJDK-jmods_x64_linux_hotspot_2020-11-23-03-35.tar.gz",
                    1L,
                    "",
                    1L,
                    "2025-02-27T19:35:32Z"
                )
            )
            val binaryList = adoptBinaryMapper.toBinaryList(assets, assets, emptyMap())

            assertEquals(ImageType.jmods, binaryList[0].image_type)
        }
    }

    private fun assertParsedHotspotJfr(binaryList: List<Binary>) {
        assertEquals(JvmImpl.hotspot, binaryList[0].jvm_impl)
        assertEquals(Project.jfr, binaryList[0].project)
    }
}
