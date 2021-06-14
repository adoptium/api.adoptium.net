package net.adoptopenjdk.api

/* ktlint-disable no-wildcard-imports */
/* ktlint-enable no-wildcard-imports */
import net.adoptopenjdk.api.v3.dataSources.models.Releases
import net.adoptopenjdk.api.v3.models.VersionData
import net.adoptopenjdk.api.v3.parser.VersionParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.TreeSet
import java.util.stream.Stream

class VersionParserTest {

    data class VersionTestData(val version: VersionData, val semver: String)

    val testData = mapOf(
        Pair(
            "OpenJDK 8u212 GA Release",
            VersionTestData(
                VersionData(8, 0, 212, "", null, 0, "", "8u212"),
                "8.0.212"
            )
        ),

        Pair(
            "OpenJDK8U-jdk_x64_linux_8u222b10.tar.gz",
            VersionTestData(
                VersionData(8, 0, 222, "", null, 10, "", "8u222b10"),
                "8.0.222+10"
            )
        ),
        Pair(
            "jdk8u222-b10",
            VersionTestData(
                VersionData(8, 0, 222, "", null, 10, "", "8u222-b10"),
                "8.0.222+10"
            )
        ),
        Pair(
            "jdk-9.0.4+11",
            VersionTestData(
                VersionData(9, 0, 4, "", null, 11, "", "9.0.4+11"),
                "9.0.4+11"
            )
        ),

        Pair(
            "jdk-10.0.2+13.1",
            VersionTestData(
                VersionData(10, 0, 2, "", 1, 13, "", "10.0.2+13.1"),
                "10.0.2+13.1"
            )
        ),
        Pair(
            "jdk-11.0.4+11.4",
            VersionTestData(
                VersionData(11, 0, 4, "", 4, 11, "", "11.0.4+11.4"),
                "11.0.4+11.4"
            )
        ),

        Pair(
            "jdk-13+33_openj9-0.16.0",
            VersionTestData(
                VersionData(13, 0, 0, "", null, 33, "", "13+33"),
                "13.0.0+33"
            )
        ),

        Pair(
            "jdk-13+33",
            VersionTestData(
                VersionData(13, 0, 0, "", null, 33, "", "13+33"),
                "13.0.0+33"
            )
        ),

        Pair(
            "jdk8u152-b01-20172803",
            VersionTestData(
                VersionData(8, 0, 152, "", null, 1, "", "8u152-b01"),
                "8.0.152+1"
            )
        ),

        Pair(
            URLDecoder.decode("https://github.com/AdoptOpenJDK/openjdk11-upstream-binaries/releases/tag/jdk-11.0.5%2B10", Charset.defaultCharset()),
            VersionTestData(
                VersionData(11, 0, 5, "", null, 10, "", "11.0.5+10"),
                "11.0.5+10"
            )
        ),

        Pair(
            "jdk-11.0.4+11.4",
            VersionTestData(
                VersionData(11, 0, 4, "", 4, 11, "", "11.0.4+11.4"),
                "11.0.4+11.4"
            )
        ),

        Pair(
            "jdk13u-2019-10-30-23-10",
            VersionTestData(
                VersionData(13, 0, 0, "", null, 0, "2019-10-30-23-10", "13u-2019-10-30-23-10"),
                "13.0.0+2019-10-30-23-10"
            )
        ),
        Pair(
            "jdk-11.0.7-ea+9_openj9-0.20.0",
            VersionTestData(
                VersionData(11, 0, 7, "ea", null, 9, null, "11.0.7-ea+9"),
                "11.0.7-ea+9"
            )
        )
    )

    @Test
    fun `adopt semver works with java patch version`() {
        val parsed = VersionParser.parse("11.0.9.1+1")
        assertEquals(
            VersionData(11, 0, 9, null, null, 1, null, "11.0.9.1+1", "11.0.9+101", 1),
            parsed
        )

        assertEquals("11.0.9+101", parsed.formSemver())
    }

    @Test
    fun `parses single number`() {
        val parsed = VersionParser.parse("8", false)
        assertEquals(
            VersionData(8, 0, 0, "", null, 0, null, "8"),
            parsed
        )
        assertEquals("8.0.0", parsed.formSemver())
    }

    // TODO: remove tactical ignoring "internal" pres
    @Test
    fun `internal pre is treated as null wrt sorting`() {
        val unsorted = listOf(
            VersionData(8, 0, 282, "internal", 0, 7, "202101061709", "1.8.0_282-internal-202101061709-b07"),
            VersionData(8, 0, 282, "internal", 0, 6, "202012231721", "1.8.0_282-internal-202012231721-b06"),
            VersionData(8, 0, 282, null, 0, 7, "202101042134", "1.8.0_282-202101042134-b07"),
            VersionData(8, 0, 282, null, 0, 4, "202012071203", "1.8.0_282-202012071203-b04"),
        )
        val sorted = unsorted.sortedWith(Releases.VERSION_COMPARATOR)

        assertEquals(unsorted.get(3), sorted.get(0))
        assertEquals(unsorted.get(1), sorted.get(1))
        assertEquals(unsorted.get(0), sorted.get(2))
        assertEquals(unsorted.get(2), sorted.get(3))
    }

    @Test
    fun `patch versions sort correctly`() {
        val sorted = listOf(
            VersionData(11, 0, 7, null, null, 9, null, "11.0.7.2+9", "11.0.7+209", 2),
            VersionData(11, 0, 7, null, null, 9, null, "11.0.7+9", "11.0.7+9"),
            VersionData(11, 0, 7, null, null, 9, null, "11.0.7.1+9", "11.0.7+109", 1),
            VersionData(11, 0, 8, null, null, 9, null, "11.0.8+9", "11.0.8+9")
        )
            .sortedWith(Releases.VERSION_COMPARATOR)

        assertNull(sorted.get(0).patch)
        assertEquals(1, sorted.get(1).patch)
        assertEquals(2, sorted.get(2).patch)
        assertEquals(8, sorted.get(3).security)
    }

    @Test
    fun `null pre comes after non null`() {
        val sorted = listOf(
            VersionData(11, 0, 7, "2", null, 9, null, "11.0.7-ea+9"),
            VersionData(11, 0, 7, "1", null, 9, null, "11.0.7-ea+9"),
            VersionData(11, 0, 7, null, null, 9, null, "11.0.7-ea+9")
        )
            .sortedWith(Releases.VERSION_COMPARATOR)

        assertEquals("1", sorted.get(0).pre)
        assertEquals("2", sorted.get(1).pre)
        assertNull(sorted.get(2).pre)
    }

    @TestFactory
    fun parsesVersionsCorrectly(): Stream<DynamicTest> {
        return testData
            .map { v ->
                DynamicTest.dynamicTest(v.key) {
                    val parsed = VersionParser.parse(v.key)
                    assertTrue(v.value.version.equals(parsed), "Should be ${v.value.version.semver}, was ${parsed.semver}")
                    assertTrue(v.value.semver.equals(parsed.semver), "Should be ${v.value.semver}, was ${parsed.semver}")
                }
            }
            .stream()
    }

    @Test
    fun sortOrderIsCorrect() {
        val first = VersionParser.parse("jdk-13.0.1+9.1_openj9-0.17.0")
        val second = VersionParser.parse("jdk-13.0.1+9_openj9-0.17.0")
        val third = VersionParser.parse("jdk-13+33_openj9-0.16.0")

        val sorted = TreeSet<VersionData>()
        sorted.add(first)
        sorted.add(third)
        sorted.add(second)

        assertEquals(first, sorted.toList()[2])
        assertEquals(second, sorted.toList()[1])
        assertEquals(third, sorted.toList()[0])
    }
}
