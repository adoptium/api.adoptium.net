package net.adoptium.api.v3.routes

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import net.adoptium.api.v3.models.Architecture
import net.adoptium.api.v3.models.Cdxa
import net.adoptium.api.v3.models.ImageType
import net.adoptium.api.v3.models.JvmImpl
import net.adoptium.api.v3.models.OperatingSystem
import net.adoptium.api.v3.models.Project
import net.adoptium.api.v3.models.Vendor
import org.eclipse.microprofile.openapi.annotations.Operation

/**
 * Legacy alias resource that routes /v3/attestations/ to /v3/cdxas/
 * This provides backward compatibility for the old attestation endpoint naming.
 * Hidden from OpenAPI/Swagger documentation.
 */
@Path("/v3/attestations/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
class AttestationAliasResource
@Inject
constructor(
    private val cdxaResource: CdxaResource
) {

    @GET
    @Path("/release_name/{release_name}/{os}/{arch}/{image_type}/{jvm_impl}/{vendor}")
    @Operation(hidden = true)
    fun listCdxasForAssetBinary(
        @PathParam("os") os: OperatingSystem,
        @PathParam("arch") arch: Architecture,
        @PathParam("release_name") release_name: String,
        @PathParam("image_type") image_type: ImageType,
        @PathParam("jvm_impl") jvm_impl: JvmImpl,
        @PathParam("vendor") vendor: Vendor,
        @QueryParam("project") project: Project?
    ): List<Cdxa> {
        return cdxaResource.listCdxasForAssetBinary(os, arch, release_name, image_type, jvm_impl, vendor, project)
    }

    @GET
    @Path("/target_checksum/{target_checksum}")
    @Operation(hidden = true)
    fun listCdxasForTargetChecksum(
        @PathParam("target_checksum") target_checksum: String,
        @QueryParam("project") project: Project?
    ): List<Cdxa> {
        return cdxaResource.listCdxasForTargetChecksum(target_checksum, project)
    }

    @GET
    @Path("/release_name/{release_name}")
    @Operation(hidden = true)
    fun listCdxasForRelease(
        @PathParam("release_name") release_name: String,
        @QueryParam("project") project: Project?
    ): List<Cdxa> {
        return cdxaResource.listCdxasForRelease(release_name, project)
    }
}

// Made with Bob
