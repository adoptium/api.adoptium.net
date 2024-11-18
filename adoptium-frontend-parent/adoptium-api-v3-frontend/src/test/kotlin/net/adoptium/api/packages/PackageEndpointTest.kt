package net.adoptium.api.packages

import io.restassured.RestAssured
import io.restassured.response.Response
import net.adoptium.api.FrontendTest
import net.adoptium.api.testDoubles.UpdatableVersionSupplierStub
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilterFactory
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import org.hamcrest.Matchers

abstract class PackageEndpointTest : FrontendTest() {

    abstract fun getPath(): String

    fun getLatestPath(
        featureVersion: Int,
        releaseType: ReleaseType,
        os: OperatingSystem,
        arch: Architecture,
        imageType: ImageType,
        jvmImpl: JvmImpl,
        heapSize: HeapSize,
        vendor: Vendor,
        project: Project,
        cLib: CLib? = null
    ): String {
        return if (cLib != null) {
            "${getPath()}/latest/$featureVersion/$releaseType/$os/$arch/$imageType/$jvmImpl/$heapSize/$vendor?project=$project&c_lib=$cLib"
        } else {
            "${getPath()}/latest/$featureVersion/$releaseType/$os/$arch/$imageType/$jvmImpl/$heapSize/$vendor?project=$project"
        }
    }

    fun getVersionPath(
        releaseName: String,
        os: OperatingSystem,
        arch: Architecture,
        imageType: ImageType,
        jvmImpl: JvmImpl,
        heapSize: HeapSize,
        vendor: Vendor,
        project: Project,
        cLib: CLib? = null
    ): String {
        return if (cLib != null) {
            getVersionPathWithoutProject(releaseName, os, arch, imageType, jvmImpl, heapSize, vendor) + "?project=$project&c_lib=$cLib"
        } else {
            getVersionPathWithoutProject(releaseName, os, arch, imageType, jvmImpl, heapSize, vendor) + "?project=$project"
        }
    }

    fun getVersionPathWithoutProject(
        releaseName: String,
        os: OperatingSystem,
        arch: Architecture,
        imageType: ImageType,
        jvmImpl: JvmImpl,
        heapSize: HeapSize,
        vendor: Vendor
    ): String {
        return "${getPath()}/version/$releaseName/$os/$arch/$imageType/$jvmImpl/$heapSize/$vendor"
    }

    protected fun performRequest(path: String): Response {
        return RestAssured.given()
            .`when`()
            .redirects().follow(false)
            .get(path)
    }

    protected fun getClibBinary() = getRandomBinary(
        ReleaseFilterFactory(UpdatableVersionSupplierStub()).createFilter(
            featureVersion = 11,
            releaseType = ReleaseType.ea,
            vendor = Vendor.getDefault(),
        ),
        BinaryFilter(
            cLib = CLib.glibc,
            os = OperatingSystem.linux,
            imageType = ImageType.staticlibs,
            jvmImpl = JvmImpl.hotspot,
            heapSize = HeapSize.normal,
            project = Project.jdk,
            arch = Architecture.x64
        )
    )

    protected fun requestExpecting307(
        releaseGetter: (() -> Pair<Release, Binary>),
        getPath: ((Release, Binary) -> String)
    ) {
        val (release, binary) = releaseGetter()

        val path = getPath(release, binary)

        performRequest(path)
            .then()
            .statusCode(307)
            .header("location", Matchers.startsWith(binary.`package`.link))
    }

    protected fun requestSignatureExpecting307(
        releaseGetter: (() -> Pair<Release, Binary>),
        getPath: ((Release, Binary) -> String)
    ) {
        val (release, binary) = releaseGetter()

        val path = getPath(release, binary)

        performRequest(path)
            .then()
            .statusCode(307)
            .header("location", Matchers.startsWith(binary.`package`.signature_link))
    }

    protected fun requestChecksumExpecting307(
        releaseGetter: (() -> Pair<Release, Binary>),
        getPath: ((Release, Binary) -> String)
    ) {
        val (release, binary) = releaseGetter()

        val path = getPath(release, binary)

        performRequest(path)
            .then()
            .statusCode(307)
            .header("location", Matchers.startsWith(binary.`package`.checksum_link))
    }
}
