package net.adoptopenjdk.api.v3.routes.packages

import net.adoptopenjdk.api.v3.JsonMapper
import net.adoptopenjdk.api.v3.dataSources.APIDataStore
import net.adoptopenjdk.api.v3.dataSources.SortMethod
import net.adoptopenjdk.api.v3.dataSources.SortOrder
import net.adoptopenjdk.api.v3.dataSources.models.Releases.Companion.RELEASE_COMPARATOR
import net.adoptopenjdk.api.v3.filters.BinaryFilter
import net.adoptopenjdk.api.v3.filters.ReleaseFilter
import net.adoptopenjdk.api.v3.models.APIError
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.Asset
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import java.net.URI
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.core.Response

@ApplicationScoped
open class PackageEndpoint @Inject constructor(private val apiDataStore: APIDataStore) {

    open fun getReleases(
        release_name: String?,
        vendor: Vendor?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        project: Project?
    ): List<Release> {
        val releaseFilter = ReleaseFilter(releaseName = release_name, vendor = vendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project)
        return apiDataStore.getAdoptRepos().getFilteredReleases(releaseFilter, binaryFilter, SortOrder.DESC, SortMethod.DEFAULT).toList()
    }

    open fun <T : Asset> formResponse(
        releases: List<Release>,
        extractAsset: (Binary) -> T?,
        createResponse: (T) -> Response
    ): Response {
        when {
            releases.isEmpty() -> {
                return formErrorResponse(Response.Status.NOT_FOUND, "No releases match the request")
            }
            releases.size > 1 -> {
                val versions = releases
                    .map { it.release_name }
                return formErrorResponse(Response.Status.BAD_REQUEST, "Multiple releases match request: $versions")
            }
            else -> {
                val binaries = releases[0].binaries
                val packages = binaries.mapNotNull { extractAsset(it) }

                return when {
                    packages.isEmpty() -> {
                        formErrorResponse(Response.Status.NOT_FOUND, "No binaries match the request")
                    }
                    packages.size > 1 -> {
                        val names = packages.map { it.name }
                        formErrorResponse(Response.Status.BAD_REQUEST, "Multiple binaries match request: $names")
                    }
                    else -> {
                        createResponse(packages.first())
                    }
                }
            }
        }
    }

    private fun formErrorResponse(status: Response.Status, message: String): Response {
        return Response
            .status(status)
            .entity(JsonMapper.mapper.writeValueAsString(APIError(message)))
            .build()
    }

    open fun getRelease(release_type: ReleaseType?, version: Int?, vendor: Vendor?, os: OperatingSystem?, arch: Architecture?, image_type: ImageType?, jvm_impl: JvmImpl?, heap_size: HeapSize?, project: Project?): List<Release> {
        val releaseFilter = ReleaseFilter(releaseType = release_type, featureVersion = version, vendor = vendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project)
        val releases = apiDataStore.getAdoptRepos().getFilteredReleases(releaseFilter, binaryFilter, SortOrder.DESC, SortMethod.DEFAULT).toList()

        // We use updated_at and timestamp as well JIC we've made a mistake and respun the same version number twice, in which case newest wins.
        val comparator = RELEASE_COMPARATOR.thenBy { it.version_data.optional }
            .thenBy { it.updated_at.dateTime }
            .thenBy { it.timestamp.dateTime }

        return releases.sortedWith(comparator)
    }

    open fun redirectToAsset(): (Asset) -> Response {
        return { asset ->
            Response.temporaryRedirect(URI.create(asset.link)).build()
        }
    }
}
