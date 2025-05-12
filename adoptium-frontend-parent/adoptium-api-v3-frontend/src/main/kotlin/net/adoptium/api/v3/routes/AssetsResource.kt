package net.adoptium.api.v3.routes

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.ServerErrorException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import net.adoptium.api.v3.OpenApiDocs
import net.adoptium.api.v3.Pagination.defaultPageSize
import net.adoptium.api.v3.Pagination.defaultPageSizeNum
import net.adoptium.api.v3.Pagination.getResponseForPage
import net.adoptium.api.v3.Pagination.maxPageSize
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.filters.BinaryFilter
import net.adoptium.api.v3.filters.ReleaseFilterFactory
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.BinaryAssetView
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
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Assets")
@Path("/v3/assets/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AssetsResource
@Inject
constructor(
    private val apiDataStore: APIDataStore,
    private val releaseEndpoint: ReleaseEndpoint,
    private val releaseFilterFactory: ReleaseFilterFactory
) {

    @GET
    @Path("/feature_releases/{feature_version}/{release_type}")
    @Operation(
        operationId = "searchReleases",
        summary = "Returns release information",
        description = "List of information about builds that match the current query"
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "search results matching criteria",
                content = [Content(schema = Schema(type = SchemaType.ARRAY, implementation = Release::class))]
            ),
            APIResponse(responseCode = "400", description = "bad input parameter")
        ]
    )
    fun get(
        @Parameter(name = "release_type", description = OpenApiDocs.RELEASE_TYPE, required = true)
        @PathParam("release_type")
        release_type: ReleaseType,

        @Parameter(
            name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = true,
            schema = Schema(example = "8", type = SchemaType.INTEGER)
        )
        @PathParam("feature_version")
        version: Int,

        @Parameter(name = "os", description = "Operating System", required = false)
        @QueryParam("os")
        os: OperatingSystem?,

        @Parameter(name = "architecture", description = "Architecture", required = false)
        @QueryParam("architecture")
        arch: Architecture?,

        @Parameter(name = "image_type", description = "Image Type", required = false)
        @QueryParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "c_lib", description = OpenApiDocs.CLIB_TYPE, required = false)
        @QueryParam("c_lib")
        cLib: CLib?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = false)
        @QueryParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = false)
        @QueryParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = false)
        @QueryParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?,

        @Parameter(
            name = "before",
            description = "<p>Return binaries whose updated_at is before the given date/time. When a date is given the match is inclusive of the given day. <ul> <li>2020-01-21</li> <li>2020-01-21T10:15:30</li> <li>20200121</li> <li>2020-12-21T10:15:30Z</li> <li>2020-12-21+01:00</li> </ul></p> ",
            required = false
        )
        @QueryParam("before")
        before: DateTime?,

        @Parameter(
            name = "page_size", description = "Pagination page size",
            schema = Schema(defaultValue = defaultPageSize, maximum = maxPageSize, type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page_size")
        @DefaultValue(defaultPageSize)
        pageSize: Int,

        @Parameter(
            name = "page", description = "Pagination page number",
            schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page")
        @DefaultValue("0")
        page: Int,

        @Parameter(name = "sort_order", description = "Result sort order", required = false)
        @QueryParam("sort_order")
        sortOrder: SortOrder?,

        @Parameter(name = "sort_method", description = "Result sort method", required = false)
        @QueryParam("sort_method")
        sortMethod: SortMethod?,

        @Parameter(name = "show_page_count", required = false, hidden = true)
        @QueryParam("show_page_count")
        showPageCount: Boolean?,

        @Context
        uriInfo: UriInfo,
    ): Response {
        val order = sortOrder ?: SortOrder.DESC
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT
        val vendorNonNull = vendor ?: Vendor.getDefault()

        val releaseFilter = releaseFilterFactory.createFilter(releaseType = release_type, featureVersion = version, vendor = vendorNonNull, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, before, cLib)
        val repos = apiDataStore.getAdoptRepos().getFeatureRelease(version!!)

        if (repos == null) {
            throw NotFoundException()
        }

        val releases = apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(version, releaseFilter, binaryFilter, order, releaseSortMethod)

        return getResponseForPage(uriInfo, pageSize, page, releases, showPageCount ?: false)
    }

    @GET
    @Path("/release_name/{vendor}/{release_name}")
    @Operation(
        operationId = "getReleaseInfo",
        summary = "Returns release information",
        description = "List of releases with the given release name"
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "Release with the given vendor and name"
            ),
            APIResponse(responseCode = "400", description = "bad input parameter"),
            APIResponse(responseCode = "404", description = "no releases match the request"),
            APIResponse(responseCode = "500", description = "multiple releases match the request")
        ]
    )
    fun get(
        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(name = "release_name", description = "Name of the release i.e ", required = true)
        @PathParam("release_name")
        releaseName: String,

        @Parameter(name = "os", description = "Operating System", required = false)
        @QueryParam("os")
        os: OperatingSystem?,

        @Parameter(name = "architecture", description = "Architecture", required = false)
        @QueryParam("architecture")
        arch: Architecture?,

        @Parameter(name = "image_type", description = "Image Type", required = false)
        @QueryParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "c_lib", description = OpenApiDocs.CLIB_TYPE, required = false)
        @QueryParam("c_lib")
        cLib: CLib?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = false)
        @QueryParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = false)
        @QueryParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): Release {
        if (releaseName == null || releaseName.trim().isEmpty()) {
            throw BadRequestException("Must provide a releaseName")
        }

        if (vendor == null) {
            throw BadRequestException("Must provide a vendor")
        }

        val releaseFilter = releaseFilterFactory.createFilter(vendor = vendor, releaseName = releaseName.trim(), jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, heap_size, project, null, cLib)

        val releases = apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(
                releaseFilter,
                binaryFilter,
                SortOrder.DESC,
                SortMethod.DEFAULT
            )
            .toList()

        return when {
            releases.isEmpty() -> {
                throw NotFoundException("No releases found")
            }

            releases.size > 1 -> {
                throw ServerErrorException("Multiple releases match request", Response.Status.INTERNAL_SERVER_ERROR)
            }

            else -> {
                releases[0]
            }
        }
    }

    @GET
    @Path("/version/{version}")
    @Operation(
        operationId = "searchReleasesByVersion",
        summary = "Returns release information about the specified version.",
        description = "List of information about builds that match the current query "
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "search results matching criteria",
                content = [Content(schema = Schema(type = SchemaType.ARRAY, implementation = Release::class))]
            ),
            APIResponse(responseCode = "400", description = "bad input parameter")
        ]
    )
    fun getReleaseVersion(
        @Parameter(name = "version", description = OpenApiDocs.VERSION_RANGE, required = true)
        @PathParam("version")
        version: String,

        @Parameter(name = "os", description = "Operating System", required = false)
        @QueryParam("os")
        os: OperatingSystem?,

        @Parameter(name = "architecture", description = "Architecture", required = false)
        @QueryParam("architecture")
        arch: Architecture?,

        @Parameter(name = "image_type", description = "Image Type", required = false)
        @QueryParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "c_lib", description = OpenApiDocs.CLIB_TYPE, required = false)
        @QueryParam("c_lib")
        cLib: CLib?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = false)
        @QueryParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = false)
        @QueryParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = false)
        @QueryParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?,

        @Parameter(name = "lts", description = "Include only LTS releases", required = false)
        @QueryParam("lts")
        lts: Boolean?,

        @Parameter(name = "release_type", description = OpenApiDocs.RELEASE_TYPE, required = false)
        @QueryParam("release_type")
        release_type: ReleaseType?,

        @Parameter(
            name = "page_size", description = "Pagination page size",
            schema = Schema(defaultValue = defaultPageSize, maximum = maxPageSize, type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page_size")
        @DefaultValue(defaultPageSize)
        pageSize: Int?,

        @Parameter(
            name = "page", description = "Pagination page number",
            schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page")
        @DefaultValue("0")
        page: Int?,

        @Parameter(name = "sort_order", description = "Result sort order", required = false)
        @QueryParam("sort_order")
        sortOrder: SortOrder?,

        @Parameter(name = "sort_method", description = "Result sort method", required = false)
        @QueryParam("sort_method")
        sortMethod: SortMethod?,

        @Parameter(name = "show_page_count", required = false, hidden = true)
        @QueryParam("show_page_count")
        showPageCount: Boolean?,

        @Parameter(name = "semver",
            description = "Indicates that any version arguments provided in this request were Adoptium semantic versions",
            required = false,
            schema = Schema(defaultValue = "false", type = SchemaType.BOOLEAN)
        )
        @QueryParam("semver")
        @DefaultValue("false")
        semver: Boolean,

        @Context
        uriInfo: UriInfo,
    ): Response {
        val releases = releaseEndpoint.getReleases(
            sortOrder,
            sortMethod,
            version,
            release_type,
            vendor,
            lts,
            os,
            arch,
            image_type,
            jvm_impl,
            heap_size,
            project,
            cLib,
            semver
        )
        return getResponseForPage(uriInfo, pageSize, page, releases, showPageCount ?: false)
    }

    data class BinaryPermutation(
        val arch: Architecture,
        val heapSize: HeapSize,
        val imageType: ImageType,
        val os: OperatingSystem
    )

    @GET
    @Path("/latest/{feature_version}/{jvm_impl}")
    @Operation(summary = "Returns list of latest assets for the given feature version and jvm impl", operationId = "getLatestAssets")
    fun getLatestAssets(

        @Parameter(
            name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = true,
            schema = Schema(example = "8", type = SchemaType.INTEGER)
        )
        @PathParam("feature_version")
        version: Int,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = false)
        @QueryParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "os", description = "Operating System", required = false)
        @QueryParam("os")
        os: OperatingSystem?,

        @Parameter(name = "architecture", description = "Architecture", required = false)
        @QueryParam("architecture")
        arch: Architecture?,

        @Parameter(name = "image_type", description = "Image Type", required = false)
        @QueryParam("image_type")
        image_type: ImageType?,

        ): List<BinaryAssetView> {
        val binaryVendor = vendor ?: Vendor.getDefault()
        val releaseFilter = releaseFilterFactory.createFilter(ReleaseType.ga, featureVersion = version, vendor = binaryVendor, jvm_impl = jvm_impl)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, null, null)
        val releases = apiDataStore
            .getAdoptRepos()
            .getFilteredReleases(version, releaseFilter, binaryFilter, SortOrder.ASC, SortMethod.DEFAULT)

        return releases
            .flatMap { release ->
                release.binaries
                    .asSequence()
                    .map { Pair(release, it) }
            }
            .associateBy {
                BinaryPermutation(it.second.architecture, it.second.heap_size, it.second.image_type, it.second.os)
            }
            .values
            .map { BinaryAssetView(it.first.release_name, it.first.vendor, it.second, it.first.version_data, it.first.release_link) }
            .toList()
    }
}
