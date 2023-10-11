package net.adoptium.api.v3.routes

import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Schema
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/v1/")
@Schema(hidden = true)
@Produces(MediaType.TEXT_PLAIN)
class V1Route {

    // Cant find a way to match nothing and something in the same request, so need 2
    @GET
    @Schema(hidden = true)
    @Path("/{ignore: .*}")
    @Operation(hidden = true)
    fun get(): Response = reject()

    @GET
    @Schema(hidden = true)
    @Path("/")
    @Operation(hidden = true)
    fun getRoot(): Response = reject()

    private fun reject(): Response {
        return Response
            .status(Response.Status.GONE)
            .entity("REMOVED: V1 has now been removed, please see https://api.adoptium.net for the latest version")
            .build()
    }
}
