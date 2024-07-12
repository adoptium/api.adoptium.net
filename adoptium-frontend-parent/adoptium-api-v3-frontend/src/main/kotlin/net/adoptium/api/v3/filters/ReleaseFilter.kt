package net.adoptium.api.v3.filters

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.config.Ecosystem
import net.adoptium.api.v3.dataSources.VersionSupplier
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import java.util.function.Predicate

@ApplicationScoped
class ReleaseFilterFactory @Inject constructor(
    private val versionSupplier: VersionSupplier
) {
    fun createFilter(
        releaseType: ReleaseType? = null,
        featureVersion: Int? = null,
        releaseName: String? = null,
        vendor: Vendor? = null,
        versionRange: VersionRangeFilter? = null,
        lts: Boolean? = null,
        jvm_impl: JvmImpl? = null
    ): Predicate<Release> {
        return ReleaseFilter(
            versionSupplier.getLtsVersions(),
            releaseType,
            featureVersion,
            releaseName,
            vendor,
            versionRange,
            lts,
            jvm_impl
        )
    }
}

private class ReleaseFilter(
    private val ltsVersions: Array<Int>,
    private val releaseType: ReleaseType? = null,
    private val featureVersion: Int? = null,
    private val releaseName: String? = null,
    private val vendor: Vendor? = null,
    private val versionRange: VersionRangeFilter? = null,
    private val lts: Boolean? = null,
    private val jvm_impl: JvmImpl? = null
) : Predicate<Release> {
    override fun test(release: Release): Boolean {
        val ltsFilter = if (lts != null) {
            val isLts = ltsVersions.contains(release.version_data.major)
            lts == isLts
        } else {
            true
        }

        return (releaseType == null || release.release_type == releaseType) &&
            (featureVersion == null || release.version_data.major == featureVersion) &&
            (releaseName == null || release.release_name == releaseName) &&
            vendorMatches(release) &&
            (versionRange == null || versionRange.test(release.version_data)) &&
            ltsFilter
    }

    private fun vendorMatches(release: Release): Boolean {
        return if (Ecosystem.CURRENT == Ecosystem.adoptopenjdk && vendor == Vendor.adoptopenjdk) {
            if (jvm_impl == JvmImpl.openj9) {
                // if the user is requesting an openj9 from adopt, also include IBMs
                (release.vendor == Vendor.adoptopenjdk || release.vendor == Vendor.ibm)
            } else {
                // if we are in the adoptopenjdk api, and adoptopenjdk builds are requests, then also include eclipse builds
                (compareVendor(Vendor.eclipse, release.vendor) || release.vendor == Vendor.adoptopenjdk)
            }
        } else {
            (vendor == null || compareVendor(release.vendor, vendor))
        }
    }

    private fun compareVendor(a: Vendor?, b: Vendor?): Boolean {
        return if (a == Vendor.adoptium || a == Vendor.eclipse) {
            // make adoptium and eclipse synonymous
            b == Vendor.adoptium || b == Vendor.eclipse
        } else {
            a == b
        }
    }
}
