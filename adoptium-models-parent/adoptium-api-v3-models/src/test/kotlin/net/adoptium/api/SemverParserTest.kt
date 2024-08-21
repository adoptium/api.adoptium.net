package net.adoptium.api

import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.VersionParser
import net.adoptium.api.v3.parser.maven.SemverParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class SemverParserTest {

    @TestFactory
    fun `test various versions`(): Stream<DynamicTest> {
        return listOf(
            Pair("18.0.2.1+1", "18.0.2+101"),
            Pair("18.0.2+9", "18.0.2+9"),
            Pair("18.0.1+10", "18.0.1+10"),
            Pair("18+36", "18.0.0+36"),
            Pair("17.0.4.1+1", "17.0.4+101"),
            Pair("17.0.4+8", "17.0.4+8"),
            Pair("17.0.3+7", "17.0.3+7"),
            Pair("17.0.2+8", "17.0.2+8"),
            Pair("17.0.1+12", "17.0.1+12"),
            Pair("17+35", "17.0.0+35"),
            Pair("11.0.16.1+1", "11.0.16+101"),
            Pair("11.0.16+8", "11.0.16+8"),
            Pair("11.0.15+10", "11.0.15+10"),
            Pair("11.0.14.1+1", "11.0.14+101"),
            Pair("11.0.14+9", "11.0.14+9"),
            Pair("11.0.13+8", "11.0.13+8"),
            Pair("11.0.12+7", "11.0.12+7"),
            Pair("11.0.12+7", "11.0.12+7"),
            Pair("1.8.0_345-b01", "8.0.345+1"),
            // This was probably a mistake
            //Pair("1.8.0_342-b07", "8.0.342+7.1"),
            Pair("1.8.0_342-b07", "8.0.342+7"),
            Pair("1.8.0_332-b09", "8.0.332+9"),
            Pair("1.8.0_322-b06", "8.0.322+6"),
            Pair("1.8.0_312-b07", "8.0.312+7"),
            Pair("1.8.0_302-b08", "8.0.302+8"),
            Pair("1.8.0_302-b08", "8.0.302+8"),

            Pair("1.8.0_352-202208301423-b04", "8.0.352+4.0.202208301423"),
            Pair("11.0.16-beta+7-202206271301", "11.0.16-beta+7.0.202206271301"),
            Pair("19-beta+36-202208270349", "19.0.0-beta+36.0.202208270349")
        )
            .map { v ->
                DynamicTest.dynamicTest(v.first) {
                    val parsed1 = removeOjdkVersion(SemverParser.parseAdoptSemver(v.second)!!)
                    val parsed2 = removeOjdkVersion(VersionParser.parse(v.first))
                    Assertions.assertTrue(parsed1 == parsed2, "Should be ${parsed2}, was $parsed1")
                }
            }
            .stream()
    }

    private fun removeOjdkVersion(version: VersionData): Any {
        return VersionData(
            version.major,
            version.minor,
            version.security,
            version.pre,
            version.adopt_build_number,
            version.build,
            version.optional,
            "",
            version.semver,
            version.patch
        )
    }
}
