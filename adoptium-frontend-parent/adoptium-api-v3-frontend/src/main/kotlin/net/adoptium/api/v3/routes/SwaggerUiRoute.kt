package net.adoptium.api.v3.routes

import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.media.Schema
import java.net.URI
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/")
@Schema(hidden = true)
@Produces(MediaType.TEXT_PLAIN)
class SwaggerUiRoute {
    @GET
    @Schema(hidden = true)
    @Path("/{path:openapi|swagger-ui}")
    @Operation(hidden = true)
    fun redirectOpenAPIPaths(@PathParam("path") path: String): Response {
        return Response
            .status(Response.Status.FOUND)
            .location(URI("/q/$path"))
            .build()
    }
}
