package net.adoptopenjdk.api;

import io.quarkus.test.junit.QuarkusTest;
import net.adoptopenjdk.api.v3.filters.VersionRangeFilter
import net.adoptopenjdk.api.v3.parser.VersionParser
import org.junit.jupiter.api.Test;
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@QuarkusTest
class VersionRangeFilterTest {

    @Test
    fun versionRangeFilter() {
        val filter16 = VersionRangeFilter("(16,17)")
        val filter17 = VersionRangeFilter("(17,18)")

        val first = VersionParser.parse("17-beta+31-202107202346")
        val second = VersionParser.parse("17+20-202105070000")

        assertFalse(filter16.test(first))
        assertFalse(filter16.test(second))
        assertTrue(filter17.test(first))
        assertTrue(filter17.test(second))
    }
}
