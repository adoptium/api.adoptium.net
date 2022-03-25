package net.adoptium.api

/* ktlint-disable no-wildcard-imports */
/* ktlint-enable no-wildcard-imports */
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import io.vertx.core.json.JsonArray
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.BinaryAssetView
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Vendor
import org.junit.jupiter.api.Assertions
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

    @Test
    fun filtersImageType() {
        val body = RestAssured.given()
            .`when`()
            .get("${getPath()}/8/${JvmImpl.hotspot}?image_type=jdk")
            .body

        val hasNonJdk = JsonArray(body.asString())
            .map { JsonMapper.mapper.readValue(it.toString(), BinaryAssetView::class.java) }
            .any { it.binary.image_type !== ImageType.jdk }
        Assertions.assertFalse(hasNonJdk)
    }

    @Test
    fun filtersArch() {
        val body = RestAssured.given()
            .`when`()
            .get("${getPath()}/8/${JvmImpl.hotspot}?architecture=x64")
            .body

        val hasNonJdk = JsonArray(body.asString())
            .map { JsonMapper.mapper.readValue(it.toString(), BinaryAssetView::class.java) }
            .any { it.binary.architecture !== Architecture.x64 }
        Assertions.assertFalse(hasNonJdk)
    }

    @Test
    fun filtersOs() {
        val body = RestAssured.given()
            .`when`()
            .get("${getPath()}/8/${JvmImpl.hotspot}?os=linux")
            .body

        val hasNonJdk = JsonArray(body.asString())
            .map { JsonMapper.mapper.readValue(it.toString(), BinaryAssetView::class.java) }
            .any { it.binary.os !== OperatingSystem.linux }
        Assertions.assertFalse(hasNonJdk)
    }

    private fun hasEntryFor(binaries: JsonArray, os: OperatingSystem, imageType: ImageType, architecture: Architecture, vendor: Vendor): Boolean {
        return binaries
            .map { JsonMapper.mapper.readValue(it.toString(), BinaryAssetView::class.java) }
            .any { release ->
                val vendorMatch = release.vendor == vendor || vendor == Vendor.adoptopenjdk && release.vendor == Vendor.eclipse

                release.binary.os == os &&
                    release.binary.image_type == imageType &&
                    release.binary.architecture == architecture &&
                    vendorMatch
            }
    }
}
