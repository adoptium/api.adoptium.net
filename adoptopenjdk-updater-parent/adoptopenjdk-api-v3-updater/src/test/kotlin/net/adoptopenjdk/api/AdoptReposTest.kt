package net.adoptopenjdk.api

import kotlinx.coroutines.runBlocking
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.Releases
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
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertTrue

class AdoptReposTest : BaseTest() {

    @Test
    fun repoEqualityCheckIsCorrect() {
        runBlocking {
            val time = TimeSource.now()
            val a = formFeatureRelease(time)
            val b = formFeatureRelease(time)

            assertTrue { a == b }
        }
    }

    @Test
    fun `equality and hash code ignores download count`() {
        val repo = AdoptReposTestDataGenerator.generate()

        val release = repo.getFeatureRelease(8)!!.releases.nodeList.first()

        val binary = release.binaries.last()
        val installer = Installer(
            binary.installer!!.name,
            binary.installer!!.link,
            binary.installer!!.size,
            binary.installer!!.checksum,
            binary.installer!!.checksum_link,
            binary.installer!!.download_count + 1,
            binary.installer!!.signature_link,
            binary.installer!!.metadata_link
        )

        val binaries = release.binaries
            .dropLast(1)
            .plus(
                Binary(
                    binary.`package`,
                    binary.download_count + 2,
                    binary.updated_at,
                    binary.scm_ref,
                    installer,
                    binary.heap_size,
                    binary.os,
                    binary.architecture,
                    binary.image_type,
                    binary.jvm_impl,
                    binary.project
                )
            )

        val release2 = Release(
            release.id,
            release.release_type,
            release.release_link,
            release.release_name,
            release.timestamp,
            release.updated_at,
            binaries.toTypedArray(),
            release.download_count + 3,
            release.vendor,
            release.version_data,
            release.source
        )
        val repo2 = repo.removeRelease(8, release).addRelease(8, release2)

        assertTrue { repo == repo2 }
        assertTrue { repo.hashCode() == repo2.hashCode() }
    }

    private fun formFeatureRelease(time: ZonedDateTime): FeatureRelease {
        return FeatureRelease(
            8,
            Releases(
                listOf(
                    Release(
                        "a",
                        ReleaseType.ga,
                        "b",
                        "c",
                        DateTime(TimeSource.now()),
                        DateTime(time),
                        arrayOf(
                            Binary(
                                Package(
                                    "a",
                                    "b",
                                    1L,
                                    "v",
                                    "c",
                                    3,
                                    "d",
                                    "e"
                                ),
                                2L,
                                DateTime(time),
                                "d",
                                Installer(
                                    "a",
                                    "b",
                                    1L,
                                    "v",
                                    "c",
                                    4,
                                    "d",
                                    "e"
                                ),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            )
                        ),
                        2,
                        Vendor.adoptopenjdk,
                        VersionData(
                            1,
                            2,
                            3,
                            "a",
                            4,
                            5,
                            "c",
                            "d"
                        )

                    )
                )
            )
        )
    }
}
