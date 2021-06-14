package net.adoptopenjdk.api

/* ktlint-disable no-wildcard-imports */
/* ktlint-enable no-wildcard-imports */
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.vertx.core.json.JsonArray
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.BinaryAssetView
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Vendor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@QuarkusTest
@ExtendWith(value = [DbExtension::class])
class LatestAssetsPathTest : FrontendTest() {

    fun getPath() = "/v3/assets/latest"

    @Test
    fun latestAssetsReturnsSaneList() {
        val body = RestAssured.given()
            .`when`()
            .get("${getPath()}/8/${JvmImpl.hotspot}")
            .body

        val binaries = JsonArray(body.asString())

        assert(hasEntryFor(binaries, OperatingSystem.linux, ImageType.jdk, Architecture.x64, Vendor.getDefault()))
        assert(hasEntryFor(binaries, OperatingSystem.linux, ImageType.jre, Architecture.x64, Vendor.getDefault()))
        assert(hasEntryFor(binaries, OperatingSystem.windows, ImageType.jdk, Architecture.x64, Vendor.getDefault()))
        assert(hasEntryFor(binaries, OperatingSystem.windows, ImageType.jre, Architecture.x64, Vendor.getDefault()))
    }

    private fun hasEntryFor(binaries: JsonArray, os: OperatingSystem, imageType: ImageType, architecture: Architecture, vendor: Vendor): Boolean {
        return binaries
            .map { JsonMapper.mapper.readValue(it.toString(), BinaryAssetView::class.java) }
            .filter { release ->
                release.binary.os == os &&
                    release.binary.image_type == imageType &&
                    release.binary.architecture == architecture &&
                    release.vendor == vendor
            }
            .count() > 0
    }
}
