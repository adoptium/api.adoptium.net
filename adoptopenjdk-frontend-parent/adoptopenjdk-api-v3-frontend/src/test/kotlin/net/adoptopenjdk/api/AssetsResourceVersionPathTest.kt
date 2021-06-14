package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured
import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.config.Ecosystem
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.Vendor
import org.hamcrest.Description
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

@QuarkusTest
class AssetsResourceVersionPathTest : AssetsPathTest() {

    fun getPath() = "/v3/assets/version"

    companion object {
        val JAVA8_212 = "8.0.200+2.1"
        val RANGE_11_12 = "[11.0.0,12.0.0]"
        val RANGE_8_METADATA = "[8.0.100+3,8.0.212+5]"
        val JAVA11 = "11.0.200+2.1"
        val ABOVE_8 = "[8.0.0,)"
        val BELOW_11 = "(,11.0.0]"
    }

    @TestFactory
    fun `no Vendor Defaults To Default`(): Stream<DynamicTest> {
        return listOf(
            ABOVE_8,
            BELOW_11,
            JAVA8_212,
            RANGE_11_12,
            RANGE_8_METADATA,
            JAVA11
        )
            .map { request -> "${getPath()}/$request?PAGE_SIZE=100" }
            .map { request ->
                DynamicTest.dynamicTest(request) {
                    RestAssured.given()
                        .`when`()
                        .get(request)
                        .then()
                        .body(object : TypeSafeMatcher<String>() {

                            override fun describeTo(description: Description?) {
                                description!!.appendText("json")
                            }

                            override fun matchesSafely(p0: String?): Boolean {
                                val releases = JsonMapper.mapper.readValue(p0, Array<Release>::class.java)
                                return releases
                                    .all {
                                        return if (Ecosystem.CURRENT == Ecosystem.adoptopenjdk) {
                                            return it.vendor == Vendor.adoptopenjdk || it.vendor == Vendor.adoptium
                                        } else {
                                            it.vendor == Vendor.getDefault()
                                        }
                                    }
                            }
                        })
                        .statusCode(200)
                }
            }
            .stream()
    }

    @TestFactory
    fun filtersLts(): Stream<DynamicTest> {
        return listOf(
            Pair("${getPath()}/$JAVA8_212?lts=true", 200),
            Pair("${getPath()}/$JAVA8_212?lts=false", 404),
            Pair("${getPath()}/$ABOVE_8?lts=false", 200),
            Pair("${getPath()}/$ABOVE_8?lts=false", 200)
        ).map { request ->
            DynamicTest.dynamicTest(request.first) {
                val response = RestAssured.given()
                    .`when`()
                    .get(request.first)
                    .then()
                    .statusCode(request.second)
                if (request.second == 200) {
                    response.body("binaries.lts.flatten().size()", Matchers.greaterThan(0))
                } else {
                    response
                }
            }
        }.stream()
    }

    override fun <T> runFilterTest(filterParamName: String, values: Array<T>, customiseQuery: (T, String) -> String): Stream<DynamicTest> {

        return listOf(
            ABOVE_8,
            BELOW_11,
            JAVA8_212,
            RANGE_11_12,
            RANGE_8_METADATA,
            JAVA11
        )
            .flatMap { versionRange ->
                createTest(values, getPath() + "/" + versionRange, filterParamName, { element -> getExclusions(versionRange, element) }, customiseQuery)
            }
            .stream()
    }

    private fun <T> getExclusions(versionRange: String, element: T): Boolean {
        return versionRange.equals(JAVA8_212) && element == JvmImpl.openj9 ||
            versionRange.equals(JAVA8_212) && element == Architecture.aarch64 ||
            versionRange.equals(JAVA8_212) && element == Architecture.arm ||
            versionRange.equals(JAVA8_212) && element == HeapSize.large ||
            versionRange.equals(JAVA8_212) && element == ImageType.testimage ||

            versionRange.equals(RANGE_8_METADATA) && element == Architecture.aarch64 ||
            versionRange.equals(RANGE_8_METADATA) && element == Architecture.arm ||
            versionRange.equals(RANGE_8_METADATA) && element == ImageType.testimage ||

            versionRange.equals(RANGE_11_12) && element == OperatingSystem.solaris ||
            versionRange.equals(RANGE_11_12) && element == Architecture.sparcv9 ||

            versionRange.equals(JAVA11) && element == Architecture.x32 ||
            versionRange.equals(JAVA11) && element == OperatingSystem.solaris ||
            versionRange.equals(JAVA11) && element == Architecture.sparcv9 ||
            versionRange.equals(JAVA11) && element == ImageType.testimage ||
            versionRange.equals(BELOW_11) && element == ImageType.testimage ||

            element == Architecture.riscv64 || // Temporary until riscv ga

            element == ImageType.debugimage ||
            element == ImageType.staticlibs ||
            element == OperatingSystem.`alpine-linux` ||
            (element == JvmImpl.dragonwell).xor(versionRange.equals(JAVA8_212) || versionRange.equals(JAVA11))
    }
}
