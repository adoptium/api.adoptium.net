package net.adoptium.api.packages

import io.quarkus.test.junit.QuarkusTest
import net.adoptium.api.DbExtension
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class InstallerPathTest : PackageEndpointTest() {

    override fun getPath(): String {
        return "/v3/installer"
    }

    @Test
    fun latestDoesRedirectToBinary() {
        val path = getLatestPath(11, ReleaseType.ga, OperatingSystem.windows, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.getDefault(), Project.jdk)
        performRequest(path)
            .then()
            .statusCode(307)
            .header("location", Matchers.startsWith("https://github.com/adoptium/temurin11-binaries/releases/download/"))
    }

    @Test
    fun latestDoesRedirectToBinaryNoProject() {
        val path = "${getPath()}/latest/11/ga/windows/x64/jdk/hotspot/normal/${Vendor.getDefault()}"
        performRequest(path)
            .then()
            .statusCode(307)
            .header("location", Matchers.startsWith("https://github.com/adoptium/temurin11-binaries/releases/download/"))
    }

    @Test
    fun latestDoesNotRedirectToBinary() {
        val path = getLatestPath(8, ReleaseType.ga, OperatingSystem.windows, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.valhalla)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun noExistantLatestRequestGives404() {
        val path = getLatestPath(4, ReleaseType.ga, OperatingSystem.windows, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.valhalla)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun versionRequestRedirects() {
        requestExpecting307(::getRandomBinary) { release, binary ->
            getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)
        }
    }

    @Test
    fun nonExistantVersionRequestGives404() {
        val path = getVersionPath("fooBar", OperatingSystem.windows, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.jdk)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun `static lib and glibc latest works`() {
        requestExpecting307(::getClibBinary) { release, binary ->
            getLatestPath(release.version_data.major, release.release_type, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project, binary.c_lib)
        }
    }

    @Test
    fun `static lib and glibc version works`() {
        requestExpecting307(::getClibBinary) { release, binary ->
            getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project, binary.c_lib)
        }
    }
}
