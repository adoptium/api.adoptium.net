package net.adoptium.marketplace.server.frontend.routes

import net.adoptium.marketplace.dataSources.APIDataStore
import net.adoptium.marketplace.schema.Architecture
import net.adoptium.marketplace.schema.CLib
import net.adoptium.marketplace.schema.ImageType
import net.adoptium.marketplace.schema.JvmImpl
import net.adoptium.marketplace.schema.OperatingSystem
import net.adoptium.marketplace.schema.Release
import net.adoptium.marketplace.schema.Vendor
import net.adoptium.marketplace.server.frontend.OpenApiDocs
import net.adoptium.marketplace.server.frontend.Pagination.defaultPageSize
import net.adoptium.marketplace.server.frontend.Pagination.getPage
import net.adoptium.marketplace.server.frontend.Pagination.maxPageSize
import net.adoptium.marketplace.server.frontend.filters.BinaryFilter
import net.adoptium.marketplace.server.frontend.filters.BinaryFilterMultiple
import net.adoptium.marketplace.server.frontend.filters.ReleaseFilter
import net.adoptium.marketplace.server.frontend.filters.ReleaseFilterMultiple
import net.adoptium.marketplace.server.frontend.models.APIDateTime
import net.adoptium.marketplace.server.frontend.models.BinaryAssetView
import net.adoptium.marketplace.server.frontend.models.SortMethod
import net.adoptium.marketplace.server.frontend.models.SortOrder
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.BadRequestException
import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.ServerErrorException
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Tag(name = "Assets")
@Path("/v1/assets/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AssetsResource
@Inject
constructor(
    private val releaseEndpoint: ReleaseEndpoint,
    private val apiDataStore: APIDataStore
) {
    @GET
    @Path("/feature_releases/{vendor}/{feature_version}")
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
    suspend fun get(
        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(
            name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = true,
            schema = Schema(defaultValue = "8", type = SchemaType.INTEGER)
        )
        @PathParam("feature_version")
        version: Int?,

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

        @Parameter(
            name = "before",
            description = "<p>Return binaries whose updated_at is before the given date/time. When a date is given the match is inclusive of the given day. <ul> <li>2020-01-21</li> <li>2020-01-21T10:15:30</li> <li>20200121</li> <li>2020-12-21T10:15:30Z</li> <li>2020-12-21+01:00</li> </ul></p> ",
            required = false
        )
        @QueryParam("before")
        before: APIDateTime?,

        @Parameter(
            name = "page_size", description = "Pagination page size",
            schema = Schema(defaultValue = defaultPageSize, maximum = maxPageSize, type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page_size")
        pageSize: Int?,

        @Parameter(
            name = "page", description = "Pagination page number",
            schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page")
        page: Int?,

        @Parameter(name = "sort_order", description = "Result sort order", required = false)
        @QueryParam("sort_order")
        sortOrder: SortOrder?,

        @Parameter(name = "sort_method", description = "Result sort method", required = false)
        @QueryParam("sort_method")
        sortMethod: SortMethod?

    ): List<Release> {

        val order = sortOrder ?: SortOrder.DESC
        val releaseSortMethod = sortMethod ?: SortMethod.DEFAULT

        val releaseFilter = ReleaseFilter(featureVersion = version)
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, before, cLib)

        val releases = releaseEndpoint.getReleases(vendor, releaseFilter, binaryFilter, order, releaseSortMethod)

        return getPage(pageSize, page, releases)
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
    suspend fun get(
        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(name = "release_name", description = "Name of the release i.e ", required = true)
        @PathParam("release_name")
        releaseName: String?,

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
        jvm_impl: JvmImpl?
    ): Release {
        if (releaseName == null || releaseName.trim().isEmpty()) {
            throw BadRequestException("Must provide a releaseName")
        }

        val releaseFilter = ReleaseFilter(vendor = vendor, releaseName = releaseName.trim())
        val binaryFilter = BinaryFilter(os, arch, image_type, jvm_impl, null, cLib)

        val releases = releaseEndpoint.getReleases(vendor, releaseFilter, binaryFilter, SortOrder.DESC, SortMethod.DEFAULT).toList()

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
    @Path("/version/{vendor}/{version}")
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
    suspend fun getReleaseVersion(
        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

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

        @Parameter(name = "lts", description = "Include only LTS releases", required = false)
        @QueryParam("lts")
        lts: Boolean?,

        @Parameter(
            name = "page_size", description = "Pagination page size",
            schema = Schema(defaultValue = defaultPageSize, maximum = maxPageSize, type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page_size")
        pageSize: Int?,

        @Parameter(
            name = "page", description = "Pagination page number",
            schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false
        )
        @QueryParam("page")
        page: Int?,

        @Parameter(name = "sort_order", description = "Result sort order", required = false)
        @QueryParam("sort_order")
        sortOrder: SortOrder?,

        @Parameter(name = "sort_method", description = "Result sort method", required = false)
        @QueryParam("sort_method")
        sortMethod: SortMethod?
    ): List<Release> {
        val releases = releaseEndpoint.getReleases(
            vendor,
            sortOrder,
            sortMethod,
            version,
            lts,
            os,
            arch,
            image_type,
            jvm_impl,
            cLib
        )
        return getPage(pageSize, page, releases)
    }

    data class binaryPermutation(
        val vendor: Vendor,
        val arch: Architecture,
        val imageType: ImageType,
        val os: OperatingSystem
    )

    @GET
    @Path("/latest/{vendor}/{feature_version}/{jvm_impl}")
    @Operation(summary = "Returns list of latest assets for the given feature version and jvm impl", operationId = "getLatestAssets")
    suspend fun getLatestAssets(

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(
            name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = true,
            schema = Schema(defaultValue = "8", type = SchemaType.INTEGER)
        )
        @PathParam("feature_version")
        version: Int,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl,


        ): List<BinaryAssetView> {
        val releaseFilter = ReleaseFilter(featureVersion = version)
        val binaryFilter = BinaryFilter(null, null, null, jvm_impl, null, null)

        val releases = releaseEndpoint.getReleases(vendor, releaseFilter, binaryFilter, SortOrder.ASC, SortMethod.DEFAULT)

        return releases
            .flatMap { release ->
                release.binaries
                    .asSequence()
                    .map { Pair(release, it) }
            }
            .associateBy {
                binaryPermutation(it.first.vendor, it.second.architecture, it.second.imageType, it.second.os)
            }
            .values
            .map { BinaryAssetView(it.first.releaseName, it.first.vendor, it.second, it.first.openjdkVersionData) }
            .toList()
    }


    @GET
    @Path("/latestForVendors")
    @Operation(summary = "Returns list of latest assets for the given feature version and jvm impl", operationId = "latestForVendors")
    suspend fun latestForVendors(

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @QueryParam("vendor")
        vendors: List<Vendor>,

        @Parameter(name = "os", description = "Operating System", required = false)
        @QueryParam("os")
        os: List<OperatingSystem>?,

        @Parameter(name = "architecture", description = "Architecture", required = false)
        @QueryParam("architecture")
        arch: List<Architecture>?,

        @Parameter(name = "image_type", description = "Image Type", required = false)
        @QueryParam("image_type")
        image_type: List<ImageType>?,

        @Parameter(name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = false)
        @QueryParam("feature_version")
        version: List<Int>?

    ): List<BinaryAssetView> {

        val versions = if (version == null || version.isEmpty()) {
            apiDataStore.getReleases(Vendor.adoptium).getReleaseInfo().available_releases.toList()
        } else {
            version
        }

        return vendors
            .flatMap { vendor ->
                val releaseFilter = ReleaseFilterMultiple(versions, null, listOf(vendor), null)
                val binaryFilter = BinaryFilterMultiple(os, arch, image_type, null, null, null)

                releaseEndpoint.getReleases(vendor, releaseFilter, binaryFilter, SortOrder.ASC, SortMethod.DEFAULT)
            }
            .flatMap { release ->
                release.binaries
                    .asSequence()
                    .map { Pair(release, it) }
            }
            .associateBy {
                binaryPermutation(it.first.vendor, it.second.architecture, it.second.imageType, it.second.os)
            }
            .values
            .map { BinaryAssetView(it.first.releaseName, it.first.vendor, it.second, it.first.openjdkVersionData) }
            .toList()
    }
}
