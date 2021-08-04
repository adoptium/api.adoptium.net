package net.adoptopenjdk.api.v3.routes

import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.SortMethod
import net.adoptopenjdk.api.v3.dataSources.SortOrder
import net.adoptopenjdk.api.v3.filters.BinaryFilter
import net.adoptopenjdk.api.v3.filters.ReleaseFilter
import net.adoptopenjdk.api.v3.filters.VersionRangeFilter
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import net.adoptopenjdk.api.v3.parser.FailedToParse
import net.adoptopenjdk.api.v3.parser.maven.InvalidVersionSpecificationException
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.BadRequestException

@ApplicationScoped
class ReleaseEndpoint
@Inject
constructor(private val apiDataStore: APIDataStore) {
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
        project: Project?
    ): Sequence<Release> {
        val order = sortOrder ?: SortOrder.DESC
        val vendorNonNull = vendor ?: Vendor.getDefault()
        val sortMethod = sortMethod ?: SortMethod.DEFAULT

        val range = try {
            VersionRangeFilter(version)
        } catch (e: InvalidVersionSpecificationException) {
            throw BadRequestException("Invalid version range", e)
        } catch (e: FailedToParse) {
            throw BadRequestException("Invalid version string", e)
        }

        val releaseFilter = ReleaseFilter(releaseType = release_type, vendor = vendorNonNull, versionRange = range, lts = lts, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os = os, arch = arch, imageType = image_type, jvmImpl = jvm_impl, heapSize = heap_size, project = project)

        return apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(releaseFilter, binaryFilter, order, sortMethod)
    }
}
