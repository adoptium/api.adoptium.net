package net.adoptopenjdk.api.v3.routes

import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Schema
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

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
            .entity("REMOVED: V1 has now been removed, please see https://api.adoptopenjdk.net for the latest version")
            .build()
    }
}
