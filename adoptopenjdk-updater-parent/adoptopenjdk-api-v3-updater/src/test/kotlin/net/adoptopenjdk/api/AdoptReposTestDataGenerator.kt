package net.adoptopenjdk.api

import net.adoptopenjdk.api.v3.TimeSource
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepo
import net.adoptopenjdk.api.v3.dataSources.models.AdoptRepos
import net.adoptopenjdk.api.v3.dataSources.models.FeatureRelease
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
import net.adoptopenjdk.api.v3.models.SourcePackage
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.models.VersionData
import java.util.Random

object AdoptReposTestDataGenerator {

    var rand: Random = Random(1)
    private val TEST_VERSIONS = listOf(8, 10, 11, 12)
    private val TEST_RESOURCES = listOf(
        PermittedValues(
            ReleaseType.values().asList(),
            listOf(Vendor.adoptopenjdk),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal)
        ),
        PermittedValues(
            ReleaseType.values().asList(),
            listOf(Vendor.adoptopenjdk),
            listOf(Project.jdk),
            listOf(JvmImpl.openj9),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            HeapSize.values().asList()
        ),
        PermittedValues(
            ReleaseType.values().asList(),
            listOf(Vendor.openjdk),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal),
            listOf(8, 11)
        ),
        PermittedValues(
            ReleaseType.values().asList(),
            listOf(Vendor.alibaba),
            listOf(Project.jdk),
            listOf(JvmImpl.dragonwell),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal),
            listOf(8, 11)
        ),
        PermittedValues(
            ReleaseType.values().asList(),
            listOf(Vendor.eclipse),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal),
            listOf(8, 11, 12)
        )
    )

    fun generate(): AdoptRepos {
        rand = Random(1)
        return AdoptRepos(
            TEST_VERSIONS.associateWith { version ->
                FeatureRelease(version, createRepos(version))
            }
                .filter {
                    it.value.releases.nodeList.isNotEmpty()
                }
        )
    }

    private fun createRepos(majorVersion: Int): List<AdoptRepo> {
        return (1..2)
            .flatMap {
                TEST_RESOURCES.map { AdoptRepo(it.createReleases(majorVersion)) }
            }
            .toList()
    }

    class PermittedValues(
        val releaseType: List<ReleaseType>,
        val vendor: List<Vendor>,
        val project: List<Project>,
        val jvmImpl: List<JvmImpl>,
        val imageType: List<ImageType>,
        val architecture: List<Architecture>,
        val operatingSystem: List<OperatingSystem>,
        val heapSize: List<HeapSize>,
        val versions: List<Int> = TEST_VERSIONS
    ) {
        private fun releaseBuilder(): (ReleaseType) -> (Vendor) -> (VersionData) -> Release {
            return { releaseType: ReleaseType ->
                { vendor: Vendor ->
                    { versionData: VersionData ->
                        Release(
                            randomString("release id"),
                            releaseType,
                            randomString("release lin"),
                            randomString("release name"),
                            randomDate(),
                            randomDate(),
                            getBinaries(),
                            2,
                            vendor,
                            versionData,
                            sourcePackage()
                        )
                    }
                }
            }
        }

        private fun createPackage(): Package {
            return Package(
                randomString("package name"),
                "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/",
                rand.nextLong(),
                randomString("checksum"),
                randomString("checksum link"),
                1,
                randomString("signature link"),
                randomString("metadata link")
            )
        }

        private fun createInstaller(): Installer {
            return Installer(
                randomString("installer name"),
                "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/",
                2,
                randomString("checksum"),
                randomString("checksum link"),
                3,
                randomString("signature link"),
                randomString("metadata link")
            )
        }

        private fun exhaustiveBinaryList(): List<Binary> {
            return HeapSize.values()
                .map {
                    Binary(
                        createPackage(),
                        1,
                        randomDate(),
                        randomString("scm ref"),
                        createInstaller(),
                        it,
                        OperatingSystem.linux,
                        Architecture.x64,
                        ImageType.jdk,
                        JvmImpl.hotspot,
                        Project.jdk
                    )
                }
                .union(
                    OperatingSystem.values()
                        .map {
                            Binary(
                                createPackage(),
                                1,
                                randomDate(),
                                randomString("scm ref"),
                                createInstaller(),
                                HeapSize.normal,
                                it,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            )
                        }
                )
                .union(
                    Architecture.values()
                        .map {
                            Binary(
                                createPackage(),
                                1,
                                randomDate(),
                                randomString("scm ref"),
                                createInstaller(),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                it,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                Project.jdk
                            )
                        }
                )
                .union(
                    ImageType.values()
                        .map {
                            Binary(
                                createPackage(),
                                1,
                                randomDate(),
                                randomString("scm ref"),
                                createInstaller(),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                it,
                                JvmImpl.hotspot,
                                Project.jdk
                            )
                        }
                )
                .union(
                    JvmImpl.values()
                        .map {
                            Binary(
                                createPackage(),
                                1,
                                randomDate(),
                                randomString("scm ref"),
                                createInstaller(),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                it,
                                Project.jdk
                            )
                        }
                )
                .union(
                    Project.values()
                        .map {
                            Binary(
                                createPackage(),
                                1,
                                randomDate(),
                                randomString("scm ref"),
                                createInstaller(),
                                HeapSize.normal,
                                OperatingSystem.linux,
                                Architecture.x64,
                                ImageType.jdk,
                                JvmImpl.hotspot,
                                it
                            )
                        }
                )
                .toList()
        }

        private fun binaryBuilder(): (HeapSize) -> (OperatingSystem) -> (Architecture) -> (ImageType) -> (JvmImpl) -> (Project) -> Binary {
            return { heapSize ->
                { operatingSystem ->
                    { architecture ->
                        { imageType ->
                            { jvmImpl ->
                                { project ->
                                    Binary(
                                        createPackage(),
                                        1,
                                        randomDate(),
                                        randomString("scm ref"),
                                        createInstaller(),
                                        heapSize,
                                        operatingSystem,
                                        architecture,
                                        imageType,
                                        jvmImpl,
                                        project
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun createReleases(majorVersion: Int): List<Release> {
            if (!versions.contains(majorVersion)) {
                return emptyList()
            }
            return releaseType
                .map { releaseBuilder()(it) }
                .flatMap { builder -> vendor.map { builder(it) } }
                .flatMap { builder -> getVersions(majorVersion).map { builder(it) } }
                .filter { it.binaries.isNotEmpty() }
                .filter {
                    Vendor.validVendor(it.vendor)
                }
        }

        private fun getBinaries(): Array<Binary> {
            return heapSize.map { binaryBuilder()(it) }
                .flatMap { builder -> operatingSystem.map { builder(it) } }
                .flatMap { builder -> architecture.map { builder(it) } }
                .flatMap { builder -> imageType.map { builder(it) } }
                .flatMap { builder -> jvmImpl.map { builder(it) } }
                .flatMap { builder -> project.map { builder(it) } }
                .union(exhaustiveBinaryList())
                .filter { binary ->
                    JvmImpl.validJvmImpl(binary.jvm_impl)
                }
                .distinctBy {
                    listOf(it.architecture, it.heap_size, it.image_type, it.jvm_impl, it.os, it.project)
                }
                .toTypedArray()
        }
    }

    private fun sourcePackage(): SourcePackage {
        return SourcePackage(randomString("source package name"), randomString("source package link"), rand.nextLong())
    }

    fun getVersions(majorVersion: Int): List<VersionData> {
        return listOf(
            VersionData(majorVersion, 0, 200, null, 1, 2, null, ""),
            VersionData(majorVersion, 0, 201, null, 1, 2, null, "")
        )
    }

    private fun randomDate(): DateTime {
        return DateTime(TimeSource.now().minusDays(10))
    }

    fun randomString(prefix: String): String {
        return prefix + ": " + rand.nextInt().toString()
    }
}
