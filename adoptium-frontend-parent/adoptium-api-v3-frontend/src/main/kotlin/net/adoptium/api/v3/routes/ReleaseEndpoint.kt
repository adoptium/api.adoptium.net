package net.adoptium.api.v3.routes

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilterFactory
import net.adoptium.api.v3.filters.VersionRangeFilter
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.parser.FailedToParse
import net.adoptium.api.v3.parser.maven.InvalidVersionSpecificationException

@ApplicationScoped
class ReleaseEndpoint
@Inject
constructor(
    private val apiDataStore: APIDataStore,
    private val releaseFilterFactory: ReleaseFilterFactory
) {
    fun getReleases(
        sortOrder: SortOrder?,
        sortMethod: SortMethod?,
        version: String?,
        release_type: ReleaseType?,
        vendor: Vendor?,
        lts: Boolean?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        project: Project?,
        cLib: CLib?,
        semver: Boolean?
    ): Sequence<Release> {
        val order = sortOrder ?: SortOrder.DESC
        val vendorNonNull = vendor ?: Vendor.getDefault()
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT

        val range = try {
            VersionRangeFilter(version, semver ?: false)
        } catch (e: InvalidVersionSpecificationException) {
            throw BadRequestException("Invalid version range", e)
        } catch (e: FailedToParse) {
            throw BadRequestException("Invalid version string", e)
        }

        val releaseFilter = releaseFilterFactory.createFilter(releaseType = release_type, vendor = vendorNonNull, versionRange = range, lts = lts, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os = os, arch = arch, imageType = image_type, jvmImpl = jvm_impl, heapSize = heap_size, project = project, cLib = cLib)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(releaseFilter, binaryFilter, order, releaseSortMethod)
    }
}
