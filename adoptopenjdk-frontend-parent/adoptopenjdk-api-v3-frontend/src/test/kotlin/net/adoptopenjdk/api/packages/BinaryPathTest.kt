package net.adoptopenjdk.api.packages

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptopenjdk.api.DbExtension
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class BinaryPathTest : PackageEndpointTest() {

    override fun getPath(): String {
        return "/v3/binary"
    }

    @Test
    fun latestDoesRedirectToBinary() {

        val (release, binary) = getRandomBinary()

        val path = getLatestPath(release.version_data.major, release.release_type, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)

        performRequest(path)
            .then()
            .statusCode(307)
            .header("Location", Matchers.equalTo(binary.`package`.link))
    }

    @Test
    fun latestDoesRedirectToBinaryNoProject() {
        val path = "${getPath()}/latest/11/ga/linux/x64/jdk/hotspot/normal/${Vendor.getDefault()}"
        performRequest(path)
            .then()
            .statusCode(307)
            .header("Location", Matchers.startsWith("https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/"))
    }

    @Test
    fun latestDoesNotRedirectToBinary() {
        val path = getLatestPath(1, ReleaseType.ga, OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.valhalla)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun nonExistantLatestRequestGives404() {
        val path = getLatestPath(4, ReleaseType.ga, OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.valhalla)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun versionRequestRedirects() {
        val (release, binary) = getRandomBinary()
        val path = getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)

        performRequest(path)
            .then()
            .statusCode(307)
            .header("Location", Matchers.startsWith(binary.`package`.link))
    }

    @Test
    fun nonExistantVersionRequestGives404() {
        val path = getVersionPath("fooBar", OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.jdk)
        performRequest(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun gradleHeadRequestToVersionGives200() {
        val (release, binary) = getRandomBinary()

        val path = getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)

        RestAssured.given()
            .`when`()
            .header("user-agent", "Gradle")
            .head(path)
            .then()
            .statusCode(200)
            .header("size", Matchers.equalTo(binary.`package`.size.toString()))
            .header("content-disposition", Matchers.equalTo("""attachment; filename="${binary.`package`.name}"; filename*=UTF-8''${binary.`package`.name}"""))
    }

    @Test
    fun nonGradleHeadRequestToVersionGives307() {
        val (release, binary) = getRandomBinary()

        val path = getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)

        RestAssured.given()
            .`when`()
            .redirects().follow(false)
            .head(path)
            .then()
            .statusCode(307)
    }

    @Test
    fun nonExistantHeadVersionRequestGives404() {
        val path = getVersionPath("fooBar", OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.jdk)

        RestAssured.given()
            .`when`()
            .header("user-agent", "Gradle")
            .head(path)
            .then()
            .statusCode(404)
    }

    @Test
    fun versionPathWithoutProjectDefaultsToJdk() {
        RestAssured.given()
            .`when`()
            .header("user-agent", "Gradle")
            .head(
                getVersionPathWithoutProject("jdk8u212-b04", OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk)
            )
            .then()
            .header("content-disposition", Matchers.not(Matchers.contains("jfr")))
    }
}
