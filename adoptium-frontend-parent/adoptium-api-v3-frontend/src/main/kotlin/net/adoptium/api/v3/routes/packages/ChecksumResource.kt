package net.adoptium.api.v3.routes.packages

import net.adoptium.api.v3.OpenApiDocs
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Binary
import net.adoptium.api.v3.models.CLib
import net.adoptium.api.v3.models.HeapSize
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Release
import net.adoptium.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import net.adoptium.api.v3.models.Package

@Tag(name = "Checksum")
@Path("/v3/checksum/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class ChecksumResource @Inject constructor(private val packageEndpoint: PackageEndpoint) {

    @GET
    @Path("/version/{release_name}/{os}/{arch}/{image_type}/{jvm_impl}/{heap_size}/{vendor}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        operationId = "getChecksumByVersion",
        summary = "Redirects to the checksum of the release that matches your current query",
        description = "Redirects to the checksum of the release that matches your current query"
    )
    @APIResponses(
        value = [
            APIResponse(responseCode = "307", description = "link to checksum of the release that matches your current query"),
            APIResponse(responseCode = "400", description = "bad input parameter"),
            APIResponse(responseCode = "404", description = "No matching signature found")
        ]
    )
    fun returnChecksumByVersion(
        @Parameter(name = "os", description = "Operating System", required = true)
        @PathParam("os")
        os: OperatingSystem,

        @Parameter(name = "arch", description = "Architecture", required = true)
        @PathParam("arch")
        arch: Architecture,

        @Parameter(
            name = "release_name", description = OpenApiDocs.RELEASE_NAME, required = true,
            schema = Schema(example = "jdk-11.0.6+10", type = SchemaType.STRING)
        )
        @PathParam("release_name")
        release_name: String,

        @Parameter(name = "image_type", description = "Image Type", required = true)
        @PathParam("image_type")
        image_type: ImageType,

        @Parameter(name = "c_lib", description = OpenApiDocs.CLIB_TYPE, required = false)
        @QueryParam("c_lib")
        cLib: CLib?,

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl,

        @Parameter(name = "heap_size", description = "Heap Size", required = true)
        @PathParam("heap_size")
        heap_size: HeapSize,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): Response {
        val releases = packageEndpoint.getReleases(release_name, vendor, os, arch, image_type, jvm_impl, heap_size, project, cLib)
        return formSignatureResponse(releases)
    }

    private fun formSignatureResponse(releases: List<Release>): Response {
        return packageEndpoint.formResponse(releases, extractPackage(), packageEndpoint.redirectToAssetChecksum())
    }

    private fun extractPackage(): (binary: Binary) -> Package {
        return { binary -> binary.`package` }
    }
}
