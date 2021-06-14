package net.adoptopenjdk.api

import net.adoptopenjdk.api.testDoubles.InMemoryApiPersistence
import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.DateTime
import net.adoptopenjdk.api.v3.models.GitHubDownloadStatsDbEntry
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
import net.adoptopenjdk.api.v3.stats.DockerStatsInterface
import net.adoptopenjdk.api.v3.stats.GitHubDownloadStatsCalculator
import org.junit.jupiter.api.Test

class StatsCalculatorTest : BaseTest() {

    private fun gitHubDownloadStatsCalculator() = GitHubDownloadStatsCalculator(InMemoryApiPersistence(adoptRepos))

    @Test
    fun testGithubStatsCalculator() {
        val adoptRepos = AdoptRepos(listOf(generateFeatureRelease()))
        val result: List<GitHubDownloadStatsDbEntry> = gitHubDownloadStatsCalculator().getStats(adoptRepos)

        assert(result[0].feature_version == 8)
        assert(result[0].downloads == 895L)
        assert(result[0].jvmImplDownloads!![JvmImpl.hotspot] == 565L)
        assert(result[0].jvmImplDownloads!![JvmImpl.openj9] == 330L)
    }

    @Test
    fun testDockerVersionNumber() {
        assert(DockerStatsInterface.getOpenjdkVersionFromString("openjdk11") == 11)
        assert(DockerStatsInterface.getOpenjdkVersionFromString("openjdk7") == 7)
        assert(DockerStatsInterface.getOpenjdkVersionFromString("openjdk") == null)
        assert(DockerStatsInterface.getOpenjdkVersionFromString("blah") == null)
    }

    private fun generateFeatureRelease(): FeatureRelease {
        val time = TimeSource.now()

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
                                Package("a", "b", 1L, "v", "c", 12L, "d", "e"),
                                15L, // Download count
                                DateTime(time),
                                "d",
                                Installer("a", "b", 1L, "v", "c", 3L, "d", "e"),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            ),
                            Binary(
                                Package("a", "b", 1L, "v", "c", 250L, "d", "e"),
                                250L,
                                DateTime(time),
                                "d",
                                null,
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            ),
                            Binary(
                                Package("a", "b", 1L, "v", "c", 60L, "d", "e"),
                                60L,
                                DateTime(time),
                                "d",
                                null,
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.openj9,
                                Project.jdk
                            ),
                            Binary(
                                Package("a", "b", 1L, "v", "c", 120L, "d", "e"),
                                120L,
                                DateTime(time),
                                "d",
                                null,
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.openj9,
                                Project.jdk
                            )
                        ),
                        445,
                        Vendor.adoptopenjdk,
                        VersionData(1, 2, 3, "a", 4, 5, "c", "d")
                    ),
                    Release(
                        "b",
                        ReleaseType.ga,
                        "b",
                        "c",
                        DateTime(TimeSource.now()),
                        DateTime(time),
                        arrayOf(
                            Binary(
                                Package("a", "b", 1L, "v", "c", 300L, "d", "e"),
                                300L, // Download count
                                DateTime(time),
                                "d",
                                Installer("a", "b", 1L, "v", "c", 3L, "d", "e"),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            ),
                            Binary(
                                Package("a", "b", 1L, "v", "c", 150L, "d", "e"),
                                150L,
                                DateTime(time),
                                "d",
                                null,
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.openj9,
                                Project.jdk
                            )
                        ),
                        450,
                        Vendor.adoptopenjdk,
                        VersionData(1, 2, 3, "a", 4, 5, "c", "d")
                    )
                )
            )
        )
    }
}
