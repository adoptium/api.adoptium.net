package net.adoptium.api.v3.routes.info

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import net.adoptium.api.v3.OpenApiDocs
import net.adoptium.api.v3.Pagination
import net.adoptium.api.v3.Pagination.defaultPageSize
import net.adoptium.api.v3.Pagination.formPagedResponse
import net.adoptium.api.v3.Pagination.getPage
import net.adoptium.api.v3.dataSources.SortMethod
import net.adoptium.api.v3.dataSources.SortOrder
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.ReleaseList
import net.adoptium.api.v3.models.ReleaseType
import net.adoptium.api.v3.models.ReleaseVersionList
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.routes.ReleaseEndpoint
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Release Info")
@Path("/v3/info")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class ReleaseListResource
@Inject
constructor(
    private val releaseEndpoint: ReleaseEndpoint
) {

    @GET
    @Path("/release_names")
    @Operation(summary = "Returns a list of all release names", operationId = "getReleaseNames")
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "A list of all release names",
                content = [Content(schema = Schema(type = SchemaType.OBJECT, implementation = ReleaseList::class))]
            )
        ]
    )
    fun get(
        @Parameter(name = "release_type", description = OpenApiDocs.RELEASE_TYPE, required = false)
        @QueryParam("release_type")
        release_type: ReleaseType?,

        @Parameter(name = "version", description = OpenApiDocs.VERSION_RANGE, required = false)
        @QueryParam("version")
        version: String?,

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
        project: Project?,

        @Parameter(name = "lts", description = "Include only LTS releases", required = false)
        @QueryParam("lts")
        lts: Boolean?,

        @Parameter(name = "page_size", description = "Pagination page size", schema = Schema(defaultValue = defaultPageSize, maximum = Pagination.maxPageSize, type = SchemaType.INTEGER), required = false)
        @QueryParam("page_size")
        @DefaultValue(defaultPageSize)
        pageSize: Int,

        @Parameter(name = "page", description = "Pagination page number", schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false)
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

        @Parameter(name = "semver",
            description = "Indicates that any version arguments provided in this request were Adoptium semantic versions",
            required = false,
            schema = Schema(defaultValue = "false", type = SchemaType.BOOLEAN)
        )
        @QueryParam("semver")
        @DefaultValue("false")
        semver: Boolean?,

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
            .map { it.release_name }
            .distinct()

        val pagedReleases = getPage(pageSize, page, releases, showPageCount ?: false)

        return formPagedResponse(ReleaseList(pagedReleases.data.toTypedArray()), uriInfo, pagedReleases)
    }

    @Path("/release_versions")
    @GET
    @Operation(summary = "Returns a list of all release versions", operationId = "getReleaseVersions")
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "A list of all release versions",
                content = [Content(schema = Schema(type = SchemaType.OBJECT, implementation = ReleaseVersionList::class))]
            )
        ]
    )
    fun getVersions(
        @Parameter(name = "release_type", description = OpenApiDocs.RELEASE_TYPE, required = false)
        @QueryParam("release_type")
        release_type: ReleaseType?,

        @Parameter(name = "version", description = OpenApiDocs.VERSION_RANGE, required = false)
        @QueryParam("version")
        version: String?,

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
        project: Project?,

        @Parameter(name = "lts", description = "Include only LTS releases", required = false)
        @QueryParam("lts")
        lts: Boolean?,

        @Parameter(name = "page_size", description = "Pagination page size", schema = Schema(defaultValue = defaultPageSize, maximum = Pagination.largerPageSize, type = SchemaType.INTEGER), required = false)
        @QueryParam("page_size")
        @DefaultValue(defaultPageSize)
        pageSize: Int?,

        @Parameter(name = "page", description = "Pagination page number", schema = Schema(defaultValue = "0", type = SchemaType.INTEGER), required = false)
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
        semver: Boolean?,

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
            .map { it.version_data }
            .distinct()

        val pagedReleases = getPage(pageSize, page, releases, showPageCount ?: false, maxPageSizeNum = Pagination.largerPageSizeNum)

        return formPagedResponse(ReleaseVersionList(pagedReleases.data.toTypedArray()), uriInfo, pagedReleases)
    }
}
