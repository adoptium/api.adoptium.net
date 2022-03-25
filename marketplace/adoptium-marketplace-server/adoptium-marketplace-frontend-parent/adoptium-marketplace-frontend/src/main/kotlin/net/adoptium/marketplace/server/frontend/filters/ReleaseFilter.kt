package net.adoptium.marketplace.server.frontend.filters

import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.Vendor
import java.util.function.Predicate

class ReleaseFilter(
    private val featureVersion: Int? = null,
    private val releaseName: String? = null,
    private val vendor: Vendor? = null,
    private val versionRange: VersionRangeFilter? = null,
    private val lts: Boolean? = null,
) : Predicate<Release> {
    override fun test(release: Release): Boolean {
        return (featureVersion == null || release.openjdkVersionData.major == featureVersion) &&
            (releaseName == null || release.releaseName == releaseName) &&
            (vendor == null || vendor == release.vendor) &&
            (versionRange == null || versionRange.test(release.openjdkVersionData)) &&
            (lts == null || release.openjdkVersionData.isLts == lts)
    }
}
