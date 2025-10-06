package net.adoptium.api

import net.adoptium.api.v3.TimeSource
import net.adoptium.api.v3.dataSources.models.AdoptRepo
import net.adoptium.api.v3.dataSources.models.AdoptRepos
import net.adoptium.api.v3.dataSources.models.FeatureRelease
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
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.SourcePackage
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.VersionData
import java.util.*

object AdoptReposTestDataGenerator {

    var rand: Random = Random(1)
    private val TEST_VERSIONS = listOf(8, 10, 11, 12)
    private val TEST_RESOURCES = listOf(
        PermittedValues(
            ReleaseType.entries,
            listOf(Vendor.adoptopenjdk),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal),
        ),
        PermittedValues(
            ReleaseType.entries,
            listOf(Vendor.adoptopenjdk),
            listOf(Project.jdk),
            listOf(JvmImpl.openj9),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            HeapSize.entries
        ),
        PermittedValues(
            ReleaseType.entries,
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
            ReleaseType.entries,
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
            ReleaseType.entries,
            listOf(Vendor.eclipse),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.jre, ImageType.jdk),
            listOf(Architecture.x64, Architecture.x32, Architecture.arm),
            listOf(OperatingSystem.linux, OperatingSystem.mac, OperatingSystem.windows),
            listOf(HeapSize.normal),
            listOf(8, 11, 12)
        ),
        PermittedValues(
            ReleaseType.entries,
            listOf(Vendor.eclipse),
            listOf(Project.jdk),
            listOf(JvmImpl.hotspot),
            listOf(ImageType.staticlibs),
            listOf(Architecture.x64),
            listOf(OperatingSystem.linux),
            listOf(HeapSize.normal),
            listOf(8, 11, 12),
            listOf(CLib.glibc, CLib.musl)
        )
    )

    fun generate(includeReleaseWithNoGas: Boolean = true): AdoptRepos {
        rand = Random(1)

        val repo = AdoptRepos(
            TEST_VERSIONS.associateWith { version ->
                FeatureRelease(version, createRepos(version))
            }
                .filter {
                    it.value.releases.nodeList.isNotEmpty()
                })

        if (!includeReleaseWithNoGas) {
            return repo
        }

        return repo.addRelease(
            18, generate18Release()
        )
    }

    private fun generate18Release() = Release(
        "a",
        ReleaseType.ea,
        "b",
        "jdk-18.0.2+101",
        randomDate(),
        randomDate(),
        arrayListOf(
            Binary(
                Package(
                    randomString("package name"),
                    "https://github.com/adoptium/temurin11-binaries/releases/download/",
                    rand.nextLong(),
                    randomString("checksum"),
                    "https://github.com/adoptium/temurin11-binaries/releases/download/",
                    1,
                    "https://github.com/adoptium/temurin11-binaries/releases/download/",
                    randomString("metadata link")
                ),
                0,
                randomDate(),
                "",
                null,
                HeapSize.normal,
                OperatingSystem.linux,
                Architecture.x64,
                ImageType.jdk,
                JvmImpl.hotspot,
                Project.jdk,
                null
            )
        ).toTypedArray(),
        1,
        Vendor.getDefault(),
        VersionData(
            18,
            0,
            2,
            null,
            1,
            1,
            null,
            "18.0.2.1+1",
            "18.0.2+101",
            1
        ),
        null
    )

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
        val versions: List<Int> = TEST_VERSIONS,
        val cLib: List<CLib>? = null
    ) {
        private fun releaseBuilder(): (ReleaseType) -> (Vendor) -> (VersionData) -> Release {
            return { releaseType: ReleaseType ->
                { vendor: Vendor ->
                    { versionData: VersionData ->
                        Release(
                            randomString("release id"),
                            releaseType,
                            randomString("release lin"),
                            randomString("release name " + versionData.semver + " "),
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
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                rand.nextLong(),
                randomString("checksum"),
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                1,
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                randomString("metadata link")
            )
        }

        private fun createInstaller(): Installer {
            return Installer(
                randomString("installer name"),
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                2,
                randomString("checksum"),
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                3,
                "https://github.com/adoptium/temurin11-binaries/releases/download/",
                randomString("metadata link")
            )
        }

        private fun exhaustiveBinaryList(): List<Binary> {
            return HeapSize.entries
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
                        Project.jdk,
                        null
                    )
                }
                .union(
                    OperatingSystem.entries
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
                                Project.jdk,
                                null
                            )
                        }
                )
                .union(
                    Architecture.entries
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
                                Project.jdk,
                                null
                            )
                        }
                )
                .union(
                    ImageType.entries
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
                                Project.jdk,
                                null
                            )
                        }
                )
                .union(
                    JvmImpl.entries
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
                                Project.jdk,
                                null
                            )
                        }
                )
                .union(
                    Project.entries
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
                                it,
                                null
                            )
                        }
                )
                .toList()
        }

        private fun binaryBuilder(): (HeapSize) -> (OperatingSystem) -> (Architecture) -> (ImageType) -> (JvmImpl) -> (Project) -> (CLib?) -> Binary {
            return { heapSize ->
                { operatingSystem ->
                    { architecture ->
                        { imageType ->
                            { jvmImpl ->
                                { project ->
                                    { cLib ->
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
                                            project,
                                            cLib,
                                        )
                                    }
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
                .asSequence()
                .map { releaseBuilder()(it) }
                .flatMap { builder -> vendor.map { builder(it) } }
                .flatMap { builder -> getVersions(majorVersion).map { builder(it) } }
                .filter { it.binaries.isNotEmpty() }
                .filter {
                    Vendor.validVendor(it.vendor)
                }
                .toList()
        }

        private fun getBinaries(): Array<Binary> {
            return heapSize.map { binaryBuilder()(it) }
                .flatMap { builder -> operatingSystem.map { builder(it) } }
                .flatMap { builder -> architecture.map { builder(it) } }
                .flatMap { builder -> imageType.map { builder(it) } }
                .flatMap { builder -> jvmImpl.map { builder(it) } }
                .flatMap { builder -> project.map { builder(it) } }
                .flatMap { builder ->
                    cLib?.map { builder(it) } ?: listOf(builder(null))
                }
                .union(exhaustiveBinaryList())
                .filter { binary ->
                    JvmImpl.validJvmImpl(binary.jvm_impl)
                }
                .distinctBy {
                    listOf(it.architecture, it.heap_size, it.image_type, it.jvm_impl, it.os, it.project, it.c_lib)
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
