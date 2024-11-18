package net.adoptium.api.v3.routes.packages

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import net.adoptium.api.v3.JsonMapper
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.dataSources.models.Releases.Companion.RELEASE_COMPARATOR
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilterFactory
import net.adoptium.api.v3.models.APIError
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Asset
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.Vendor
import java.net.URI

@ApplicationScoped
open class PackageEndpoint @Inject constructor(
    private val apiDataStore: APIDataStore,
    private val releaseFilterFactory: ReleaseFilterFactory) {

    open fun getReleases(
        release_name: String?,
        vendor: Vendor?,
        os: OperatingSystem?,
        arch: Architecture?,
        image_type: ImageType?,
        jvm_impl: JvmImpl?,
        heap_size: HeapSize?,
        project: Project?,
        cLib: CLib?
    ): List<Release> {
        val releaseFilter = releaseFilterFactory.createFilter(releaseName = release_name, vendor = vendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, null, cLib)
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

    open fun getRelease(release_type: ReleaseType?, version: Int?, vendor: Vendor?, os: OperatingSystem?, arch: Architecture?, image_type: ImageType?, jvm_impl: JvmImpl?, heap_size: HeapSize?, project: Project?, cLib: CLib?): List<Release> {
        val releaseFilter = releaseFilterFactory.createFilter(releaseType = release_type, featureVersion = version, vendor = vendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, null, cLib)
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

    open fun redirectToAssetSignature(): (Asset) -> Response {
        return { asset ->
            Response.temporaryRedirect(URI.create(asset.signature_link)).build()
        }
    }

    open fun redirectToAssetChecksum(): (Asset) -> Response {
        return { asset ->
            Response.temporaryRedirect(URI.create(asset.checksum_link)).build()
        }
    }
}
