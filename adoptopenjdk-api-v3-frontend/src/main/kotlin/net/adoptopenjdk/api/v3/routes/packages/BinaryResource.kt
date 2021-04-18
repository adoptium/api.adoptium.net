package net.adoptopenjdk.api.v3.routes.packages

import net.adoptopenjdk.api.v3.OpenApiDocs
import net.adoptopenjdk.api.v3.models.Architecture
import net.adoptopenjdk.api.v3.models.Binary
import net.adoptopenjdk.api.v3.models.HeapSize
import net.adoptopenjdk.api.v3.models.ImageType
import net.adoptopenjdk.api.v3.models.JvmImpl
import net.adoptopenjdk.api.v3.models.OperatingSystem
import net.adoptopenjdk.api.v3.models.Package
import net.adoptopenjdk.api.v3.models.Project
import net.adoptopenjdk.api.v3.models.Release
import net.adoptopenjdk.api.v3.models.ReleaseType
import net.adoptopenjdk.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Tag(name = "Binary")
@Path("/v3/binary/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class BinaryResource @Inject constructor(private val packageEndpoint: PackageEndpoint) {

    @GET
    @Path("/version/{release_name}/{os}/{arch}/{image_type}/{jvm_impl}/{heap_size}/{vendor}")
    @Produces("application/octet-stream")
    @Operation(
        operationId = "getBinaryByVersion",
        summary = "Redirects to the binary that matches your current query",
        description = "Redirects to the binary that matches your current query"
    )
    @APIResponses(
        value = [
            APIResponse(responseCode = "307", description = "link to binary that matches your current query"),
            APIResponse(responseCode = "400", description = "bad input parameter"),
            APIResponse(responseCode = "404", description = "No matching binary found")
        ]
    )
    fun returnBinaryByVersion(
        @Parameter(name = "os", description = "Operating System", required = true)
        @PathParam("os")
        os: OperatingSystem?,

        @Parameter(name = "arch", description = "Architecture", required = true)
        @PathParam("arch")
        arch: Architecture?,

        @Parameter(
            name = "release_name", description = OpenApiDocs.RELASE_NAME, required = true,
            schema = Schema(defaultValue = "jdk-11.0.6+10", type = SchemaType.STRING)
        )
        @PathParam("release_name")
        release_name: String?,

        @Parameter(name = "image_type", description = "Image Type", required = true)
        @PathParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = true)
        @PathParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): Response {
        val releases = packageEndpoint.getReleases(release_name, vendor, os, arch, image_type, jvm_impl, heap_size, project)
        return formResponse(releases)
    }

    @HEAD
    @Path("/version/{release_name}/{os}/{arch}/{image_type}/{jvm_impl}/{heap_size}/{vendor}")
    @Produces("application/octet-stream")
    @Operation(
        operationId = "getBinaryByVersionHead",
        summary = "Redirects to the binary that matches your current query",
        description = "Redirects to the binary that matches your current query"
    )
    @APIResponses(
        value = [
            APIResponse(responseCode = "307", description = "link to binary that matches your current query"),
            APIResponse(responseCode = "400", description = "bad input parameter"),
            APIResponse(responseCode = "404", description = "No matching binary found")
        ]
    )
    fun returnBinaryByVersionHead(
        @Parameter(name = "os", description = "Operating System", required = true)
        @PathParam("os")
        os: OperatingSystem?,

        @Parameter(name = "arch", description = "Architecture", required = true)
        @PathParam("arch")
        arch: Architecture?,

        @Parameter(
            name = "release_name", description = OpenApiDocs.RELASE_NAME, required = true,
            schema = Schema(defaultValue = "jdk-11.0.6+10", type = SchemaType.STRING)
        )
        @PathParam("release_name")
        release_name: String?,

        @Parameter(name = "image_type", description = "Image Type", required = true)
        @PathParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = true)
        @PathParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?,

        @Parameter(hidden = true, required = false)
        @HeaderParam("User-Agent")
        userAgent: String
    ): Response {
        val releases = packageEndpoint.getReleases(release_name, vendor, os, arch, image_type, jvm_impl, heap_size, project)
        return if (userAgent.contains("Gradle")) {
            return formResponse(releases) { `package` ->
                Response.status(200)
                    .header("size", `package`.size)
                    .header("content-disposition", """attachment; filename="${`package`.name}"; filename*=UTF-8''${`package`.name}""")
                    .build()
            }
        } else {
            formResponse(releases)
        }
    }

    @GET
    @Path("/latest/{feature_version}/{release_type}/{os}/{arch}/{image_type}/{jvm_impl}/{heap_size}/{vendor}")
    @Produces("application/octet-stream")
    @Operation(
        operationId = "getBinary",
        summary = "Redirects to the binary that matches your current query",
        description = "Redirects to the binary that matches your current query"
    )
    @APIResponses(
        value = [
            APIResponse(responseCode = "307", description = "link to binary that matches your current query"),
            APIResponse(responseCode = "400", description = "bad input parameter"),
            APIResponse(responseCode = "404", description = "No matching binary found")
        ]
    )
    fun returnBinary(
        @Parameter(
            name = "feature_version", description = OpenApiDocs.FEATURE_RELEASE, required = true,
            schema = Schema(defaultValue = "8", type = SchemaType.INTEGER)
        )
        @PathParam("feature_version")
        version: Int?,

        @Parameter(name = "release_type", description = OpenApiDocs.RELEASE_TYPE, required = true)
        @PathParam("release_type")
        release_type: ReleaseType?,

        @Parameter(name = "os", description = "Operating System", required = true)
        @PathParam("os")
        os: OperatingSystem?,

        @Parameter(name = "arch", description = "Architecture", required = true)
        @PathParam("arch")
        arch: Architecture?,

        @Parameter(name = "image_type", description = "Image Type", required = true)
        @PathParam("image_type")
        image_type: ImageType?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl?,

        @Parameter(name = "heap_size", description = "Heap Size", required = true)
        @PathParam("heap_size")
        heap_size: HeapSize?,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor?,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): Response {
        val releaseList = packageEndpoint.getRelease(release_type, version, vendor, os, arch, image_type, jvm_impl, heap_size, project)

        val release = releaseList.lastOrNull()

        return formResponse(if (release == null) emptyList() else listOf(release))
    }

    protected fun formResponse(
        releases: List<Release>,
        createResponse: (Package) -> Response = packageEndpoint.redirectToAsset()
    ): Response {
        return packageEndpoint.formResponse(releases, extractPackage(), createResponse)
    }

    private fun extractPackage(): (binary: Binary) -> Package {
        return { binary -> binary.`package` }
    }
}
