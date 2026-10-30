package net.adoptium.api

/* ktlint-disable no-wildcard-imports */
/* ktlint-enable no-wildcard-imports */
import net.adoptium.api.v3.dataSources.models.Releases
import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.VersionParser
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
    fun `parses jep 322 version with downstream fifth element`() {
        listOf("25.0.5.0.2+1", "jdk-25.0.5.0.2+1").forEach { name ->
            listOf(false, true).forEach { exact ->
                val parsed = VersionParser.parse(name, exactMatch = exact)
                assertEquals(listOf(25, 0, 5, 0, 2), parsed.version_numbers, "$name exact=$exact")
                assertEquals(25, parsed.major)
                assertEquals(5, parsed.security)
                assertEquals(0, parsed.patch)
                assertEquals(1, parsed.build)
                assertEquals("25.0.5.0.2+1", parsed.openjdk_version)
                assertEquals(listOf(2), parsed.additional_version_numbers)
            }
        }
    }

    @Test
    fun `does not start parsing part way through a dotted version`() {
        listOf(false, true).forEach { exact ->
            val parsed = VersionParser.parse("25.0.11.0.2+1", exactMatch = exact)
            assertEquals(25, parsed.major, "exact=$exact")
            assertEquals(11, parsed.security, "exact=$exact")
            assertEquals(listOf(2), parsed.additional_version_numbers, "exact=$exact")
            assertEquals("25.0.11.0.2+1", parsed.openjdk_version, "exact=$exact")
        }
        assertEquals(25, VersionParser.parse("jdk-25.0.11.0.2+1").major)
    }

    @Test
    fun `default version numbers are derived`() {
        assertEquals(listOf(11, 0, 9), VersionParser.parse("jdk-11.0.9+11").version_numbers)
        assertEquals(listOf(11, 0, 9, 1), VersionParser.parse("11.0.9.1+1").version_numbers)
    }

    @Test
    fun `additional version numbers sort pointwise`() {
        val versions = listOf("25.0.5.1+1", "25.0.5.0.2+1", "25.0.5+1", "25.0.5.0.1+1", "25.0.5.0.10+1")
            .map { VersionParser.parse(it, exactMatch = true) }
        val sorted = versions.sortedWith(Releases.VERSION_COMPARATOR).map { it.openjdk_version }
        assertEquals(
            listOf("25.0.5+1", "25.0.5.0.1+1", "25.0.5.0.2+1", "25.0.5.0.10+1", "25.0.5.1+1"),
            sorted
        )
        assertEquals(sorted, versions.sorted().map { it.openjdk_version })
        assertTrue(
            !VersionParser.parse("25.0.5.0.2+1", exactMatch = true)
                .compareVersionNumber(VersionParser.parse("25.0.5.0.3+1", exactMatch = true))
        )
    }

    @Test
    fun `version numbers survive json round trip`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val parsed = VersionParser.parse("25.0.5.0.2+1")
        val copy = mapper.readValue(mapper.writeValueAsString(parsed), VersionData::class.java)
        assertEquals(parsed, copy)
        assertEquals(listOf(25, 0, 5, 0, 2), copy.version_numbers)
        assertEquals(listOf(2), copy.additional_version_numbers)
        assertTrue(mapper.writeValueAsString(parsed).contains("\"additional_version_numbers\":[2]"))
        assertEquals(emptyList<Int>(), mapper.readValue("""{"major":11,"minor":0,"security":9,"build":1,"openjdk_version":"11.0.9+1","semver":"11.0.9+1"}""", VersionData::class.java).additional_version_numbers)
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

    @Test
    fun sortOrderIsCorrectWithPres() {
        val first = VersionParser.parse("17-beta+31-202107202346")
        val second = VersionParser.parse("17+20-202105070000")

        val sorted = TreeSet<VersionData>()
        sorted.add(second)
        sorted.add(first)

        assertEquals(first, sorted.toList()[0])
        assertEquals(second, sorted.toList()[1])
    }
}
