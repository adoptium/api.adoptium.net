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
import net.adoptium.api.v3.dataSources.APIDataStore
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Vendor
import net.adoptium.api.v3.models.Attestation
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType
import org.eclipse.microprofile.openapi.annotations.media.Content
import org.eclipse.microprofile.openapi.annotations.media.Schema
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Tag(name = "Attestations")
@Path("/v3/attestations/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AttestationResource
@Inject
constructor(
    private val apiDataStore: APIDataStore
) {

    @GET
    @Path("/release_name/{release_name}/{os}/{arch}/{image_type}/{jvm_impl}/{vendor}")
    @Operation(
        operationId = "listAttestationsForAssetBinary",
        summary = "Returns matching attestations",
        description = "Return the attestations that matches the given query"
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "Attestations for the given asset binary",
                content = [Content(schema = Schema(type = SchemaType.ARRAY, implementation = Attestation::class))]
            ),
            APIResponse(responseCode = "404", description = "No matching attestations found")
        ]
    )
    fun listAttestationsForAssetBinary(
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

        @Parameter(name = "jvm_impl", description = "JVM Implementation", required = true)
        @PathParam("jvm_impl")
        jvm_impl: JvmImpl,

        @Parameter(name = "vendor", description = OpenApiDocs.VENDOR, required = true)
        @PathParam("vendor")
        vendor: Vendor,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): List<Attestation> {
        val attestations = apiDataStore
            .getAdoptAttestationRepos()
            .listAttestationsForAssetBinary( release_name, vendor, os, arch, image_type, jvm_impl )

        if ( attestations == null || attestations.isEmpty() ) {
            throw NotFoundException("Attestations not found")
        } else {
            return attestations
        }
    }

    @GET
    @Path("/target_checksum/{target_checksum}")
    @Operation(
        operationId = "listAttestationsForTargetChecksum",
        summary = "Returns attestations that target the given SHA256 checksum",
        description = "Return the list of attestations that have the matching target binary SHA256 checksum"
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "Attestations for the given target binary SHA256 checksum",
                content = [Content(schema = Schema(type = SchemaType.ARRAY, implementation = Attestation::class))]
            ),
            APIResponse(responseCode = "404", description = "No matching attestations found")
        ]
    )
    fun listAttestationsForTargetChecksum(
        @Parameter(
            name = "target_checksum", description = "Target binary SHA256 checksum", required = true,
            schema = Schema(example = "6773cfdc56d66b75f4a88ac843b2b5854791240114cf8bb1b56fb6f7826ae436", type = SchemaType.STRING)
        )
        @PathParam("target_checksum")
        target_checksum: String,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): List<Attestation> {
        val attestations = apiDataStore
            .getAdoptAttestationRepos()
            .listAttestationsForTargetChecksum( target_checksum )

        if ( attestations == null || attestations.isEmpty() ) {
            throw NotFoundException("No attestations found")
        } else {
            return attestations
        }
    }

    @GET
    @Path("/release_name/{release_name}")
    @Operation(
        operationId = "listAttestationsForRelease",
        summary = "Returns attestations for the given release",
        description = "Return the list of attestations that match the given release name"
    )
    @APIResponses(
        value = [
            APIResponse(
                responseCode = "200", description = "Attestations for the given release name",
                content = [Content(schema = Schema(type = SchemaType.ARRAY, implementation = Attestation::class))]
            ),
            APIResponse(responseCode = "404", description = "No matching attestations found")
        ]
    )
    fun listAttestationsForRelease(
        @Parameter(
            name = "release_name", description = OpenApiDocs.RELEASE_NAME, required = true,
            schema = Schema(example = "jdk-11.0.6+10", type = SchemaType.STRING)
        )
        @PathParam("release_name")
        release_name: String,

        @Parameter(name = "project", description = "Project", required = false)
        @QueryParam("project")
        project: Project?
    ): List<Attestation> {
        val attestations = apiDataStore
            .getAdoptAttestationRepos()
            .listAttestationsForRelease( release_name )

        if ( attestations == null || attestations.isEmpty() ) {
            throw NotFoundException("No attestations found")
        } else {
            return attestations
        }
    }
}
