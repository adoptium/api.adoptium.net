package net.adoptium.api

import net.adoptium.api.v3.models.VersionData
import net.adoptium.api.v3.parser.maven.VersionRange
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionRangeTest {

    private val noPatch = VersionData(11, 0, 1, null, null, 4, null, "11.0.1+4", null, null)
    private val patch1 = VersionData(11, 0, 1, null, null, 4, null, "11.0.1.1+4", null, 1)
    private val patch2 = VersionData(11, 0, 1, null, null, 4, null, "11.0.1.2+4", null, 2)
    private val nextBuild = VersionData(11, 0, 2, null, null, 5, null, "11.0.2+5", null, 1)

    @Test
    fun `filters correctly`() {
        val range = VersionRange.createFromVersionSpec("[11.0.1.1,)", false)!!
        assertFalse(range.containsVersion(noPatch))
        assertTrue(range.containsVersion(patch1))
        assertTrue(range.containsVersion(patch2))
        assertTrue(range.containsVersion(nextBuild))
    }

    @Test
    fun `filters correctly 2`() {
        val range = VersionRange.createFromVersionSpec("(,11.0.1.1+4)", false)!!
        assertTrue(range.containsVersion(noPatch))
        assertFalse(range.containsVersion(patch1))
        assertFalse(range.containsVersion(patch2))
        assertFalse(range.containsVersion(nextBuild))
    }

    @Test
    fun `filters correctly 3`() {
        val range = VersionRange.createFromVersionSpec("(,11.0.1.1+4]", false)!!
        assertTrue(range.containsVersion(noPatch))
        assertTrue(range.containsVersion(patch1))
        assertFalse(range.containsVersion(patch2))
        assertFalse(range.containsVersion(nextBuild))
    }

    @Test
    fun `filters correctly 4`() {
        val range = VersionRange.createFromVersionSpec("(,11.0.1.2+1]", false)!!
        assertTrue(range.containsVersion(noPatch))
        assertTrue(range.containsVersion(patch1))
        assertFalse(range.containsVersion(patch2))
        assertFalse(range.containsVersion(nextBuild))
    }

    @Test
    fun `filters correctly 5`() {
        val range = VersionRange.createFromVersionSpec("[11.0.1+4,11.0.2+5)", false)!!
        assertTrue(range.containsVersion(noPatch))
        assertTrue(range.containsVersion(patch1))
        assertTrue(range.containsVersion(patch2))
        assertFalse(range.containsVersion(nextBuild))
    }

    @Test
    fun `filters correctly 6`() {
        val range = VersionRange.createFromVersionSpec("[11.0.2+4,)", false)!!
        assertFalse(range.containsVersion(noPatch))
        assertFalse(range.containsVersion(patch1))
        assertFalse(range.containsVersion(patch2))
        assertTrue(range.containsVersion(nextBuild))
    }
}
