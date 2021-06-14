package net.adoptopenjdk.api

import io.restassured.RestAssured
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.Vendor
import org.hamcrest.Matchers
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

abstract class AssetsPathTest : FrontendTest() {

    abstract fun <T> runFilterTest(
        filterParamName: String,
        values: Array<T>,
        customiseQuery: (T, String) -> String = { value, query -> query }
    ): Stream<DynamicTest>

    @TestFactory
    fun filtersOs(): Stream<DynamicTest> {
        return runFilterTest("os", OperatingSystem.values())
    }

    @TestFactory
    fun filtersArchitecture(): Stream<DynamicTest> {
        return runFilterTest("architecture", Architecture.values())
    }

    @TestFactory
    fun filtersImageType(): Stream<DynamicTest> {
        return runFilterTest("image_type", ImageType.values())
    }

    @TestFactory
    fun filtersJvmImpl(): Stream<DynamicTest> {
        return runFilterTest(
            "jvm_impl",
            JvmImpl.values().filter { JvmImpl.validJvmImpl(it) }.toTypedArray()
        ) { value, query ->
            if (value == JvmImpl.dragonwell) {
                "$query&vendor=${Vendor.alibaba.name}"
            } else {
                query
            }
        }
    }

    @TestFactory
    fun filtersHeapSize(): Stream<DynamicTest> {
        return runFilterTest("heap_size", HeapSize.values())
    }

    @TestFactory
    fun filtersProject(): Stream<DynamicTest> {
        return runFilterTest("project", arrayOf(Project.jdk, Project.jfr))
    }

    protected fun <T> createTest(
        values: Array<T>,
        path: String,
        filterParamName: String,
        exclude: (element: T) -> Boolean = { false },
        customiseQuery: (T, String) -> String
    ): List<DynamicTest> {
        return values
            .filter { !exclude(it) }
            .map { value ->
                val path2 = customiseQuery(value, "$path?$filterParamName=${value.toString().toLowerCase()}")
                DynamicTest.dynamicTest(path2) {
                    RestAssured.given()
                        .`when`()
                        .get(path2)
                        .then()
                        .statusCode(200)
                        .body("binaries.$filterParamName.flatten()", Matchers.everyItem(Matchers.`is`(value.toString())))
                        .body("binaries.$filterParamName.flatten().size()", Matchers.greaterThan(0))
                }
            }
    }
}
