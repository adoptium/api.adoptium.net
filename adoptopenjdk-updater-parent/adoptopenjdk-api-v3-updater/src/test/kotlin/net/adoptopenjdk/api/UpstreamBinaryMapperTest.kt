package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.dataSources.github.graphql.models.GHAsset
import net.adoptopenjdk.api.v3.mapping.upstream.UpstreamBinaryMapper
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.Project
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UpstreamBinaryMapperTest : BaseTest() {

    private fun getAssetList(names: List<String>): List<GHAsset> {
        return names.flatMap { name ->
            listOf(
                GHAsset(
                    name,
                    1L,
                    "",
                    1L,
                    "2013-02-27T19:35:32Z"
                ),
                GHAsset(
                    "$name.sign",
                    1L,
                    "a-signature-link to $name",
                    1L,
                    "2013-02-27T19:35:32Z"
                )
            )
        }
    }

    @Test
    fun filtersOutDebugInfo() {
        val assets = getAssetList(
            listOf(
                "OpenJDK8U-jdk_x64_linux_8u232b09.tar.gz",
                "OpenJDK8U-jdk_x64_linux_8u232b09-debuginfo.tar.gz"
            )
        )
        runBlocking {
            val binaryList = UpstreamBinaryMapper.toBinaryList(assets)

            assert(binaryList.size == 1)
            assertEquals(assets[0].name, binaryList[0].`package`.name)
        }
    }

    @Test
    fun correctlyClassifiesImageType() {

        val assets = getAssetList(
            listOf(
                "OpenJDK11U-x64_linux_11.0.3_7.tar.gz",
                "OpenJDK11U-x64_windows_11.0.3_6_ea.zip",
                "OpenJDK11U-testimage_x64_linux_11.0.5_10.tar.gz",
                "OpenJDK11U-static-libs_x64_linux_11.0.5_10.tar.gz",
                "OpenJDK11U-jre_aarch64_linux_11.0.5_10.tar.gz",
                "OpenJDK11U-jdk_aarch64_linux_11.0.5_10-debuginfo.tar.gz",
                "OpenJDK11U-sources_11.0.5_10.tar.gz",
                "OpenJDK11U-jdk_x64_linux_11.0.10_3_ea.tar.gz",
                "OpenJDK11U-jre-shenandoah_x64_linux_11.0.10_3_ea.tar.gz",
                "OpenJDK11U-jdk-shenandoah_x64_linux_11.0.10_3_ea.tar.gz",
                "OpenJDK11U-testimage-shenandoah_x64_linux_11.0.10_3_ea.tar.gz"
            )
        )

        runBlocking {
            val binaryList = UpstreamBinaryMapper.toBinaryList(assets)

            assertEquals(9, binaryList.size)
            assertEquals(ImageType.jdk, binaryList[0].image_type)
            assertEquals(ImageType.jdk, binaryList[1].image_type)
            assertEquals(ImageType.testimage, binaryList[2].image_type)
            assertEquals(ImageType.staticlibs, binaryList[3].image_type)
            assertEquals(ImageType.jre, binaryList[4].image_type)
            assertEquals(ImageType.jdk, binaryList[5].image_type)
            assertEquals(ImageType.jre, binaryList[6].image_type)
            assertEquals(ImageType.jdk, binaryList[7].image_type)
            assertEquals(ImageType.testimage, binaryList[8].image_type)
        }
    }

    @Test
    fun correctlyClassifiesProjectType() {
        val assets = getAssetList(
            listOf(
                "OpenJDK8U-jdk-jfr_x64_linux_8u262b01_ea.tar.gz",
                "OpenJDK8U-jdk_x64_linux_8u262b01_ea.tar.gz",
                "OpenJDK8U-jre-jfr_x64_linux_8u262b01_ea.tar.gz",
                "OpenJDK8U-jre_x64_linux_8u262b01_ea.tar.gz",
                "OpenJDK11U-jre-shenandoah_x64_linux_11.0.10_3_ea.tar.gz",
                "OpenJDK11U-jdk-shenandoah_x64_linux_11.0.10_3_ea.tar.gz",
                "OpenJDK11U-testimage-shenandoah_x64_linux_11.0.10_3_ea.tar.gz"
            )
        )

        runBlocking {
            val binaryList = UpstreamBinaryMapper.toBinaryList(assets)

            assertEquals(7, binaryList.size)
            assertEquals(Project.jfr, binaryList[0].project)
            assertEquals(Project.jdk, binaryList[1].project)
            assertEquals(Project.jfr, binaryList[2].project)
            assertEquals(Project.jdk, binaryList[3].project)
            assertEquals(Project.shenandoah, binaryList[4].project)
            assertEquals(Project.shenandoah, binaryList[5].project)
            assertEquals(Project.shenandoah, binaryList[6].project)
        }
    }

    @Test
    fun addsSignatureLink() {

        val assets = getAssetList(
            listOf(
                "OpenJDK11U-x64_linux_11.0.3_7.tar.gz"
            )
        )

        runBlocking {
            val binaryList = UpstreamBinaryMapper.toBinaryList(assets)
            assertEquals("a-signature-link to OpenJDK11U-x64_linux_11.0.3_7.tar.gz", binaryList[0].`package`.signature_link)
        }
    }
}
