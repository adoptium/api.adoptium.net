package net.adoptium.api

import io.restassured.RestAssured
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Vendor
import org.hamcrest.Matchers
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.*
import java.util.stream.Stream

abstract class AssetsPathTest : FrontendTest() {

    abstract fun <T> runFilterTest(
        filterParamName: String,
        values: Array<T>,
        customiseQuery: (T, String) -> String = { value, query -> query }
    ): Stream<DynamicTest>

    @TestFactory
    fun filtersOs(): Stream<DynamicTest> {
        return runFilterTest("os", OperatingSystem.entries.toTypedArray())
    }

    @TestFactory
    fun filtersArchitecture(): Stream<DynamicTest> {
        return runFilterTest("architecture", Architecture.entries.toTypedArray())
    }

    @TestFactory
    fun filtersImageType(): Stream<DynamicTest> {
        return runFilterTest("image_type", ImageType.entries.toTypedArray())
    }

    @TestFactory
    fun `filters c_lib`(): Stream<DynamicTest> {
        return runFilterTest("c_lib", CLib.entries.toTypedArray()) { _, query ->
            "$query&image_type=staticlibs"
        }
    }

    @TestFactory
    fun filtersJvmImpl(): Stream<DynamicTest> {
        return runFilterTest(
            "jvm_impl",
            JvmImpl.entries.filter { JvmImpl.validJvmImpl(it) }.toTypedArray()
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
        return runFilterTest("heap_size", HeapSize.entries.toTypedArray())
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
                val path2 = customiseQuery(value, "$path?$filterParamName=${value.toString().lowercase(Locale.getDefault())}")
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
