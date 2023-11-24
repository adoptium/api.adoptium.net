package net.adoptium.api.v3.routes

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilter
import net.adoptium.api.v3.filters.VersionRangeFilter
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.DateTime
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor

@ApplicationScoped
class ReleaseEndpoint
@Inject
constructor(
    private val apiDataStore: APIDataStore
) {
    fun getFeatureReleasesAssets(
        release_type: ReleaseType?,
        version: Int,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        cLib: CLib?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        vendor: Vendor?,
        project: Project?,
        before: DateTime?,
        sortOrder: SortOrder?,
        sortMethod: SortMethod?
    ): Sequence<Release> {
        val order = sortOrder ?: SortOrder.DESC
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT
        val vendorNonNull = vendor ?: Vendor.getDefault()

        val releaseFilter = ReleaseFilter(releaseType = release_type, featureVersion = version, vendor = vendorNonNull, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, before, cLib)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(version, releaseFilter, binaryFilter, order, releaseSortMethod)
    }

    fun getReleaseNameAssets(
        vendor: Vendor,
        releaseName: String,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        cLib: CLib?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        project: Project?
    ): Sequence<Release> {
        val releaseFilter = ReleaseFilter(vendor = vendor, releaseName = releaseName.trim(), jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, null, cLib)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(releaseFilter, binaryFilter, SortOrder.DESC, SortMethod.DEFAULT)
    }

    fun getVersionAssets(
        versionRangeFilter: VersionRangeFilter,
        sortOrder: SortOrder?,
        sortMethod: SortMethod?,
        release_type: ReleaseType?,
        vendor: Vendor?,
        lts: Boolean?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        project: Project?,
        cLib: CLib?
    ): Sequence<Release> {
        val order = sortOrder ?: SortOrder.DESC
        val vendorNonNull = vendor ?: Vendor.getDefault()
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT

        val releaseFilter = ReleaseFilter(releaseType = release_type, vendor = vendorNonNull, versionRange = versionRangeFilter, lts = lts, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os = os, arch = arch, imageType = image_type, jvmImpl = jvm_impl, heapSize = heap_size, project = project, cLib = cLib)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(releaseFilter, binaryFilter, order, releaseSortMethod)
    }

    fun getLatestAssets(
        version: Int,
        jvm_impl: JvmImpl,
        vendor: Vendor?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?
    ): Sequence<Release> {
        val binaryVendor = vendor ?: Vendor.getDefault()

        val releaseFilter = ReleaseFilter(ReleaseType.ga, featureVersion = version, vendor = binaryVendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, null, null)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(version, releaseFilter, binaryFilter, SortOrder.ASC, SortMethod.DEFAULT)
    }
}
