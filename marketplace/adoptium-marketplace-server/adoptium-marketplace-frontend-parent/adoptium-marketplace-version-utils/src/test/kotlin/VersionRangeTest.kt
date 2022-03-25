package net.adoptium.api.marketplace.parser.maven

import net.adoptium.marketplace.server.frontend.versions.VersionParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class VersionRangeTest {

    @TestFactory
    fun badRangeThrows(): Stream<DynamicTest> {
        return listOf(
            "",
            "8]",
            "[8",
            "[]",
            "foo",
            "[foo]",
            "foo",
            "[8.0.1]foo",
            "foo[8.0.1]",
        ).map {
            DynamicTest.dynamicTest("test $it") {
                Assertions.assertThrows(VersionRange.InvalidVersionRange::class.java) {
                    VersionRange.parse(it)
                }
            }
        }
            .stream()
    }

    @TestFactory
    fun shouldContain(): Stream<DynamicTest> {
        return listOf(
            "8.0.1",

            "[8.0.1]",

            "[8.0.1,]",
            "[,8.0.1]",
            "[8.0.1,8.0.3]",

            "[8.0.1,)",
            "[,8.0.2)",
            "[8,9)",

            "(8,]",
            "(,8.0.1]",
            "(8,9]",

            "(8,)",
            "(,8.0.3)",
            "(8,8.0.3)",
        ).map {
            DynamicTest.dynamicTest(it) {
                Assertions.assertTrue(VersionRange.parse(it).test(VersionParser.parse("8.0.1")))
            }
        }
            .stream()
    }

    @TestFactory
    fun shouldNotContain(): Stream<DynamicTest> {
        return listOf(
            "8.0.1",

            "(8.0.3)",
            "[8.0.1]",

            "[8.0.4,]",
            "[,8.0.2]",
            "[8.0.4,8.0.4]",

            "[8.0.4,)",
            "[,8.0.3)",
            "[8,8.0.3)",

            "(8.0.3,]",
            "(,8.0.2]",
            "(8,8.0.2]",

            "(8.0.3,)",
            "(,8.0.3)",
            "(8.0.3,8.0.3)",
        ).map {
            DynamicTest.dynamicTest(it) {
                Assertions.assertFalse(VersionRange.parse(it).test(VersionParser.parse("8.0.3")))
            }
        }
            .stream()
    }
}
