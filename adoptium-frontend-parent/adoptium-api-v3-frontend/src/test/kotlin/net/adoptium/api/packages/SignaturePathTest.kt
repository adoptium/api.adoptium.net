package net.adoptium.api.packages

import io.quarkus.test.junit.QuarkusTest
import net.adoptium.api.DbExtension
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class SignaturePathTest : PackageEndpointTest() {
    override fun getPath(): String {
        return "/v3/signature"
    }

    @Test
    fun versionRequestRedirects() {
        requestSignatureExpecting307(::getRandomBinary) { release, binary ->
            getVersionPath(release.release_name, binary.os, binary.architecture, binary.image_type, binary.jvm_impl, binary.heap_size, release.vendor, binary.project)
        }
    }

    @Test
    fun nonExistantVersionRequestGives404() {
        val path = getVersionPath("fooBar", OperatingSystem.linux, Architecture.x64, ImageType.jdk, JvmImpl.hotspot, HeapSize.normal, Vendor.adoptopenjdk, Project.jdk)
        performRequest(path)
            .then()
            .statusCode(404)
    }
}
